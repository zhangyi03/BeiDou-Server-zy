package org.gms.soloMapling.ArtificialPlayer.BotTypes.OPQ;

import java.awt.Point;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.gms.client.Character;
import org.gms.server.maps.MapleMap;
import org.gms.server.maps.Reactor;
import org.gms.soloMapling.ArtificialPlayer.BotGeneration;
import org.gms.soloMapling.ArtificialPlayer.BotHelpers;
import org.gms.soloMapling.ArtificialPlayer.BotTypes.OPQ.OPQSharedContext.OPQPhase;

/**
 * 一次 OPQ 运行的协调器（多机器人共享一块黑板）。
 *
 * <p>职责边界：
 * <ul>
 *   <li>登记/注销参与本次运行的机器人；</li>
 *   <li>跟住队长 id，并在队长进图后把机器人「拖」进同一张图
 *       （机器人没有客户端，NPC 脚本的 {@code warpEventTeam} 不会带上它们，只能服务端补）；</li>
 *   <li>分配互不冲突的云朵 / 箱子反应堆；</li>
 *   <li>轮询阶段完成条件（队长被传送到塔顶 = 第一阶段完成）；</li>
 *   <li>换一轮时清干净状态。</li>
 * </ul>
 *
 * <p><b>不负责</b>任何细粒度行为 —— 机器人自己看地图 + 黑板决定该干什么，
 * 这里是纯黑板模式。
 *
 * @author WorkBuddy
 * @since 2026-11-02
 */
@Slf4j
public class OPQOrchestrator {

    private static final long TICK_PERIOD_MS = 1000L;

    private static final OPQOrchestrator INSTANCE = new OPQOrchestrator();

    public static OPQOrchestrator getInstance() {
        return INSTANCE;
    }

    private final OPQSharedContext context = new OPQSharedContext();

    private final List<OPQBot> registeredBots = new CopyOnWriteArrayList<>();

    /**
     * 队长 id，任何机器人第一次观察到就锁住，之后每个 tick 刷新。
     */
    private volatile int leaderId = -1;

    /**
     * 第二阶段箱子反应堆的排序索引（oid -> 0~6，从右到左）。
     */
    private final Map<Integer, Integer> boxSortedIndex = new HashMap<>();

    private ScheduledExecutorService ticker;

    private ScheduledFuture<?> tickHandle;

    private OPQOrchestrator() {
    }

    private static void opqLog(String msg) {
        log.debug("[OPQOrchestrator] {}", msg);
    }

    // ------------------------------------------------------------------ 注册

    public OPQSharedContext getSharedContext() {
        return context;
    }

    public synchronized void registerBot(OPQBot bot) {
        if (bot == null) {
            return;
        }
        if (!registeredBots.contains(bot)) {
            registeredBots.add(bot);
            opqLog("登记机器人 " + bot.getChr().getName());
        }
        ensureTickRunning();
    }

    public synchronized void unregisterBot(OPQBot bot) {
        if (bot == null) {
            return;
        }
        registeredBots.remove(bot);
        int id = bot.getChr().getId();
        context.putCloudAssignment(id, null);
        context.putBoxAssignment(id, null);
        context.putPlatformAssignment(id, null);
        context.clearTaskComplete(id);
        opqLog("注销机器人 " + bot.getChr().getName());
        if (registeredBots.isEmpty()) {
            stopTick();
        }
    }

    /**
     * 机器人每次看到自己在队伍里就调一下，协调器借此锁定队长 id 并激活本轮。
     */
    public void noteLeaderFromBot(OPQBot bot) {
        if (bot.getChr().getParty() == null) {
            return;
        }
        int observed = bot.getChr().getParty().getLeaderId();
        if (observed > 0 && observed != leaderId) {
            leaderId = observed;
            opqLog("锁定队长 id=" + leaderId);
        }
        if (!context.isPqActive()) {
            context.setPqActive(true);
            opqLog("第一次观察到队长，自动激活本轮");
        }
    }

    // ------------------------------------------------------------------ 分配

    /**
     * 给这个机器人分配一个没被别人占的云朵反应堆（选最近的活着的）。
     *
     * @return 反应堆 oid，没有可用的时候返回 null
     */
    public synchronized Integer assignCloudReactor(OPQBot bot) {
        int id = bot.getChr().getId();
        MapleMap map = bot.getChr().getMap();
        if (map == null) {
            return null;
        }
        Integer existing = context.getMyCloudAssignment(id);
        if (existing != null) {
            Reactor r = map.getReactorByOid(existing);
            if (r != null && r.isAlive() && r.getState() < 4) {
                return existing;
            }
            opqLog("云朵分配已失效 oid=" + existing + "，重新挑一个（" + bot.getChr().getName() + "）");
            context.putCloudAssignment(id, null);
        }

        Collection<Integer> taken = context.getCloudAssignmentsView().values();
        Reactor best = null;
        double bestDist = Double.MAX_VALUE;
        Point botPos = bot.getChr().getPosition();
        for (Reactor r : map.getAllReactors()) {
            if (!isCloudReactor(r)) {
                continue;
            }
            if (!r.isAlive() || r.getState() >= 4) {
                continue;
            }
            if (taken.contains(r.getObjectId())) {
                continue;
            }
            double d = botPos.distance(r.getPosition());
            if (d < bestDist) {
                bestDist = d;
                best = r;
            }
        }
        if (best == null) {
            opqLog("没有空闲云朵给 " + bot.getChr().getName()
                    + "（图上反应堆 " + map.getAllReactors().size() + " 个，已占 " + taken.size() + "）");
            return null;
        }
        int pickedOid = best.getObjectId();
        context.putCloudAssignment(id, pickedOid);
        opqLog("分配云朵 oid=" + pickedOid + "（dataId=" + best.getId()
                + " pos=" + best.getPosition() + "）-> " + bot.getChr().getName());
        return pickedOid;
    }

    /**
     * 图上还有没有「活着且没被别的机器人占」的云朵。
     */
    public boolean hasUnclaimedLiveCloudReactor(MapleMap map, int askingBotId) {
        if (map == null) {
            return false;
        }
        Collection<Integer> taken = context.getCloudAssignmentsView().values();
        Integer mine = context.getMyCloudAssignment(askingBotId);
        for (Reactor r : map.getAllReactors()) {
            if (!isCloudReactor(r) || !r.isAlive() || r.getState() >= 4) {
                continue;
            }
            int oid = r.getObjectId();
            if (taken.contains(oid) && (mine == null || mine != oid)) {
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * 给这个机器人分配一个没被别人占的箱子反应堆（只挑 state == 0 的，避免抢真人的）。
     */
    public synchronized Integer assignBoxReactor(OPQBot bot) {
        int id = bot.getChr().getId();
        MapleMap map = bot.getChr().getMap();
        if (map == null) {
            return null;
        }
        buildBoxSortedIndex(map);

        Integer existing = context.getMyBoxAssignment(id);
        if (existing != null) {
            Reactor r = map.getReactorByOid(existing);
            if (r != null && r.isAlive() && r.getState() < 4) {
                return existing;
            }
            opqLog("箱子分配已失效 oid=" + existing + "，重新挑一个（" + bot.getChr().getName() + "）");
            context.putBoxAssignment(id, null);
        }

        Collection<Integer> taken = context.getBoxAssignmentsView().values();
        Reactor best = null;
        double bestDist = Double.MAX_VALUE;
        Point botPos = bot.getChr().getPosition();
        for (Reactor r : map.getAllReactors()) {
            if (!isBoxReactor(r) || !r.isAlive() || r.getState() >= 4) {
                continue;
            }
            if (taken.contains(r.getObjectId())) {
                continue;
            }
            // 被别人敲过一半的箱子跳过（可能是真人正在打）
            if (r.getState() > 0) {
                continue;
            }
            double d = botPos.distance(r.getPosition());
            if (d < bestDist) {
                bestDist = d;
                best = r;
            }
        }
        if (best == null) {
            opqLog("没有空闲箱子给 " + bot.getChr().getName()
                    + "（图上反应堆 " + map.getAllReactors().size() + " 个，已占 " + taken.size() + "）");
            return null;
        }
        int pickedOid = best.getObjectId();
        context.putBoxAssignment(id, pickedOid);
        return pickedOid;
    }

    public boolean hasUnclaimedLiveBoxReactor(MapleMap map, int askingBotId) {
        if (map == null) {
            return false;
        }
        Collection<Integer> taken = context.getBoxAssignmentsView().values();
        for (Reactor r : map.getAllReactors()) {
            if (!isBoxReactor(r) || !r.isAlive() || r.getState() >= 4) {
                continue;
            }
            int oid = r.getObjectId();
            if (taken.contains(oid)) {
                continue;
            }
            if (r.getState() > 0) {
                continue;
            }
            return true;
        }
        return false;
    }

    /**
     * 预留一个第二阶段平台（目前只用于日志/调试，实际走反应堆分配）。
     */
    public synchronized String assignPlatformTarget(OPQBot bot) {
        int id = bot.getChr().getId();
        String existing = context.getMyPlatformAssignment(id);
        if (existing != null) {
            return existing;
        }
        String pick = pickUnusedString(OPQConstants.STAGE_2_BOX_PLATFORMS,
                context.getPlatformAssignmentsView().values());
        if (pick != null) {
            context.putPlatformAssignment(id, pick);
        }
        return pick;
    }

    // ------------------------------------------------------------------ 生命周期

    public synchronized void resetForNewRun() {
        opqLog("重置为新一轮");
        context.reset();
        context.setPqActive(true);
        context.setCurrentPhase(OPQPhase.RECRUITMENT);
        leaderId = -1;
        boxSortedIndex.clear();
    }

    public void mirrorPhase(OPQPhase phase) {
        OPQPhase prev = context.getCurrentPhase();
        if (prev != phase) {
            context.setCurrentPhase(phase);
            opqLog("阶段镜像：" + prev + " -> " + phase);
        }
    }

    public synchronized void shutdownRun() {
        opqLog("关闭本轮");
        context.reset();
        leaderId = -1;
        boxSortedIndex.clear();
    }

    // ------------------------------------------------------------------ tick

    private void ensureTickRunning() {
        if (tickHandle != null && !tickHandle.isCancelled() && !tickHandle.isDone()) {
            return;
        }
        if (ticker == null) {
            ticker = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "bot-opq-orchestrator");
                t.setDaemon(true);
                return t;
            });
        }
        tickHandle = ticker.scheduleWithFixedDelay(this::safeTick,
                TICK_PERIOD_MS, TICK_PERIOD_MS, TimeUnit.MILLISECONDS);
    }

    private void stopTick() {
        if (tickHandle != null) {
            tickHandle.cancel(false);
            tickHandle = null;
        }
    }

    private void safeTick() {
        try {
            tick();
        } catch (Throwable t) {
            log.warn("[OPQOrchestrator] tick 异常：{}", t.toString());
        }
    }

    /**
     * 每个 tick 只做两件事：阶段完成判定。
     *
     * <p>第一阶段完成的信号不是机器人掉了多少云片，而是<b>队长被 NPC 传送到塔顶</b>
     * —— 也就是 Chamberlain 出现在当前地图上。
     */
    private void tick() {
        if (!context.isPqActive()) {
            return;
        }
        if (allBotsLostParty()) {
            opqLog("所有机器人都不在队伍里了，重置本轮");
            resetForNewRun();
            return;
        }
        switch (context.getCurrentPhase()) {
            case STAGE_1 -> {
                if (!context.isStage1Complete() && isChamberlainSpawned()) {
                    opqLog("第一阶段完成 —— Chamberlain 已出现");
                    context.setStage1Complete(true);
                    clearCompletionFlagsForRegistered();
                }
            }
            case STAGE_2 -> {
                if (!context.isStage2Complete() && allRegisteredBotsComplete()) {
                    opqLog("第二阶段完成 —— 所有机器人报告完成");
                    context.setStage2Complete(true);
                    clearCompletionFlagsForRegistered();
                }
            }
            default -> {
                // 其余阶段没有需要协调的事
            }
        }
    }

    private Character resolveLeader() {
        if (leaderId <= 0 || registeredBots.isEmpty()) {
            return null;
        }
        OPQBot anchor = registeredBots.get(0);
        if (anchor.getChr().getClient() == null || anchor.getChr().getClient().getChannelServer() == null) {
            return null;
        }
        return anchor.getChr().getClient().getChannelServer()
                .getPlayerStorage().getCharacterById(leaderId);
    }

    public boolean isChamberlainSpawned() {
        Character leader = resolveLeader();
        return leader != null && BotHelpers.isNpcPresent(leader.getMap(), OPQConstants.CHAMBERLAIN_EAK);
    }

    /**
     * 把机器人拉到队长所在的地图 + 指定落点。
     *
     * <p>日志刻意带上 cid 与来源/目标地图：排查「进副本后有的机器人不见了」时，
     * 只看 cid 就能确认某一台到底是被拉走了、还是被遣返了、还是根本没动。
     */
    public void followLeaderWarp(Character bot, Point pt) {
        if (bot == null) {
            return;
        }
        Character leader = resolveLeader();
        if (leader == null || leader.getMap() == null) {
            opqLog("拉人失败 " + bot.getName() + "（cid=" + bot.getId() + "）：找不到队长（leaderId="
                    + leaderId + "，登记机器人 " + registeredBots.size() + " 台）");
            return;
        }
        int fromMap = bot.getMapId();
        try {
            boolean ok = BotGeneration.warpBotToLocation(bot, pt, leader.getMap());
            opqLog("拉人 " + bot.getName() + "（cid=" + bot.getId() + "）" + fromMap + " -> "
                    + leader.getMap().getId() + " @ " + pt + "：" + (ok ? "成功" : "失败")
                    + (ok ? "" : "，当前 map=" + bot.getMapId()));
        } catch (Exception e) {
            opqLog("拉人失败 " + bot.getName() + "（cid=" + bot.getId() + "）：" + e);
        }
    }

    // ------------------------------------------------------------------ 工具

    private boolean allBotsLostParty() {
        if (registeredBots.isEmpty()) {
            return false;
        }
        for (OPQBot bot : registeredBots) {
            if (bot.getChr().getParty() != null) {
                return false;
            }
        }
        return true;
    }

    private boolean allRegisteredBotsComplete() {
        if (registeredBots.isEmpty()) {
            return false;
        }
        for (OPQBot bot : registeredBots) {
            if (!context.isMyTaskComplete(bot.getChr().getId())) {
                return false;
            }
        }
        return true;
    }

    private void clearCompletionFlagsForRegistered() {
        for (OPQBot bot : registeredBots) {
            context.clearTaskComplete(bot.getChr().getId());
        }
    }

    private static boolean isCloudReactor(Reactor r) {
        return r.getId() == OPQConstants.STAGE_1_CLOUD_REACTOR_ID;
    }

    private static boolean isBoxReactor(Reactor r) {
        int id = r.getId();
        return id >= OPQConstants.STAGE_2_BOX_REACTOR_FIRST
                && id <= OPQConstants.STAGE_2_BOX_REACTOR_LAST;
    }

    /**
     * 按 x 从右到左给箱子编号，编号决定「第几个箱子掉哪张唱片」。
     */
    private void buildBoxSortedIndex(MapleMap map) {
        if (!boxSortedIndex.isEmpty()) {
            return;
        }
        List<Reactor> boxes = new ArrayList<>();
        for (Reactor r : map.getAllReactors()) {
            if (isBoxReactor(r)) {
                boxes.add(r);
            }
        }
        boxes.sort(Comparator.comparingInt((Reactor r) -> r.getPosition().x).reversed());
        for (int i = 0; i < boxes.size(); i++) {
            boxSortedIndex.put(boxes.get(i).getObjectId(), i);
            opqLog("箱子编号 pos=" + i + "（" + ordinalOf(i) + "）oid=" + boxes.get(i).getObjectId()
                    + " dataId=" + boxes.get(i).getId()
                    + " x=" + boxes.get(i).getPosition().x);
        }
    }

    private static String ordinalOf(int idx) {
        return idx >= 0 && idx < OPQConstants.STAGE_2_BOX_ORDINALS.length
                ? OPQConstants.STAGE_2_BOX_ORDINALS[idx] : "?";
    }

    public String getBoxOrdinal(int reactorOid) {
        Integer idx = boxSortedIndex.get(reactorOid);
        return idx == null ? "?" : ordinalOf(idx);
    }

    /**
     * 这个箱子该掉哪张唱片（按编号对应固定物品，保证 7 张不重复）。
     */
    public int getBoxItemId(int reactorOid) {
        Integer idx = boxSortedIndex.get(reactorOid);
        if (idx != null && idx >= 0 && idx < OPQConstants.STAGE_2_ITEMS.size()) {
            return OPQConstants.STAGE_2_ITEMS.get(idx);
        }
        return OPQConstants.STAGE_2_ITEMS.get(0);
    }

    /**
     * 中央音乐盒（第二阶段交件点）的位置。
     */
    public static Point getMusicBoxPosition(MapleMap map) {
        if (map == null) {
            return null;
        }
        for (Reactor r : map.getAllReactors()) {
            if (r.getId() == OPQConstants.STAGE_2_MUSIC_BOX_REACTOR_ID) {
                return r.getPosition();
            }
        }
        return null;
    }

    private static String pickUnusedString(List<String> pool, Iterable<String> used) {
        Deque<String> taken = new ArrayDeque<>();
        used.forEach(taken::add);
        for (String candidate : pool) {
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
