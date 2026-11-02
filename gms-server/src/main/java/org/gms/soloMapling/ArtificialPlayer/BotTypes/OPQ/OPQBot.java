package org.gms.soloMapling.ArtificialPlayer.BotTypes.OPQ;

import java.awt.Point;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.gms.client.Character;
import org.gms.server.maps.MapItem;
import org.gms.server.maps.MapObject;
import org.gms.server.maps.Reactor;
import org.gms.soloMapling.ArtificialPlayer.BotBrain;
import org.gms.soloMapling.ArtificialPlayer.BotCommandsPack.BotActions;
import org.gms.soloMapling.ArtificialPlayer.BotCommandsPack.BotAttack;
import org.gms.soloMapling.ArtificialPlayer.BotCommandsPack.DropCommands;
import org.gms.soloMapling.ArtificialPlayer.BotGeneration;
import org.gms.soloMapling.ArtificialPlayer.BotHelpers;
import org.gms.soloMapling.ArtificialPlayer.BotLogic;
import org.gms.soloMapling.ArtificialPlayer.BotMovementSystem.BotNavigator;
import org.gms.soloMapling.ArtificialPlayer.BotPartySystem.BotPartyLogic;
import org.gms.soloMapling.ArtificialPlayer.BotTypes.OPQ.OPQSharedContext.OPQPhase;
import org.gms.soloMapling.MapVFX.CustomReactor;
import org.gms.soloMapling.movement.MovementCommands;
import org.gms.soloMapling.server.BotTiming;

import static org.gms.soloMapling.ArtificialPlayer.BotTypes.OPQ.OPQConstants.STAGE_1_COMPLETE_TP;
import static org.gms.soloMapling.ArtificialPlayer.BotTypes.OPQ.OPQConstants.STAGE_1_ENTRY_TP;

/**
 * 天空之城组队任务（Orbis PQ）机器人的状态机。
 *
 * <p><b>设计（与上游 SoloMapling 一致）</b>：
 * <ul>
 *   <li>感知驱动：每次切换都只看机器人自己能观察到的状态（当前地图、坐标、背包）加上
 *       协调器黑板的只读结果，不接受别的机器人下命令；</li>
 *   <li>地图变更即权威：被游戏传送到意料之外的地图时，按 mapId 把状态拉回正确相位；</li>
 *   <li>每个阶段是一小圈循环：寻路 → 打目标 → 捡 → 回交件点 → 丢 → 等；
 *       等待有 {@link OPQConstants#STAGE_WAIT_TIMEOUT_MS} 兜底，卡住会退回 LOOP_CHECK。</li>
 * </ul>
 *
 * <p><b>与上游唯一的差异</b>：招募大厅（200080101）里不闲逛，而是站到 web 端给这台机器人
 * 配的 {@code spawn_x} 上喊话 —— 这部分是本项目自己的需求，其余进副本后的行为一律照上游执行。
 */
@Slf4j
public class OPQBot extends BotBrain {

    // volatile：挥击链跑在 tick 之外，会读写这些字段
    private volatile OPQBotState opqBotState = OPQBotState.RESET;

    private final OPQOrchestrator orchestrator;

    private final OPQSharedContext sharedContext;

    /**
     * 大厅站位（web 端配置的 spawn_x / spawn_y）。只有 RECRUITMENT 用得到。
     */
    private final Point station;

    // 各状态的计时器 / 临时字段
    private long stageWaitStartTime;

    private long lastRecruitMessageAt;

    /**
     * 看到队长进第一阶段的时刻（跨帧计时，替代上游的 blockingSleep(3000)）。
     */
    private long stage1EntrySeenAt;

    private volatile int reactorHitsThisTarget;

    private int cloudPiecesLooted;

    /** 回交件点已经补走了几次（到点或放弃后清零）。 */
    private int stage1ReturnTries;

    /** 第二阶段回音乐盒的补走次数（同上，两段各自计数，互不干扰）。 */
    private int stage2ReturnTries;

    private volatile int lootedRecordItemId = -1;

    public OPQBot(Character character, Point station) {
        super(character);
        this.orchestrator = OPQOrchestrator.getInstance();
        this.sharedContext = orchestrator.getSharedContext();
        this.station = station == null ? new Point(OPQConstants.OPQ_LOBBY_FALLBACK_POS) : new Point(station);
        orchestrator.registerBot(this);
    }

    @Override
    public String getBrainName() {
        return "OPQBot";
    }

    @Override
    public long getTickPeriodMs() {
        return OPQConstants.TICK_PERIOD_MS;
    }

    public OPQBotState getOPQBotState() {
        return opqBotState;
    }

    /**
     * 下线时由 {@code BotService} 调用：停帧并从协调器注销，避免它继续把云朵/箱子
     * 分给一台已经不在场的机器人。
     */
    public void dispose() {
        setRunning(false);
        orchestrator.unregisterBot(this);
    }

    private void transitionTo(OPQBotState next, String reason) {
        OPQBotState prev = this.opqBotState;
        if (prev == next) {
            return;
        }
        this.opqBotState = next;
        log.debug("[OPQBot {}] {} -> {} | {} | map={} pos={}",
                getChr().getName(), prev, next, reason,
                getChr().getMapId(), getChr().getPosition());
    }

    public enum OPQBotState {
        RESET,
        RECRUITMENT,
        IN_PARTY_IDLE,
        STAGE_1_NAVIGATE,
        STAGE_1_HIT_REACTOR,
        STAGE_1_LOOT,
        STAGE_1_RETURN,
        STAGE_1_DROP_ITEMS,
        STAGE_1_WAIT,
        STAGE_1_TRANSITION,
        STAGE_1_TRANSITION_PT_2,
        STAGE_2_NAVIGATE,
        STAGE_2_HIT_BOX,
        STAGE_2_LOOT,
        STAGE_2_RETURN,
        STAGE_2_DROP_ITEMS,
        STAGE_2_WAIT,
        EXIT_DETECT,
        EXIT_LOBBY,
        LOOP_CHECK
    }

    private void resetOPQBotState() {
        opqBotState = OPQBotState.RESET;
        stageWaitStartTime = 0;
        lastRecruitMessageAt = 0;
        stage1EntrySeenAt = 0;
        reactorHitsThisTarget = 0;
        cloudPiecesLooted = 0;
        lootedRecordItemId = -1;
    }

    // =========================================================================
    // 主 tick
    // =========================================================================

    @Override
    public void updateState() {
        if (isInsidePQ() && !isInParty()) {
            log.warn("[OPQBot {}] cid={} 在副本里丢了队伍引用，state={} map={} pos={}，判定任务作废",
                    getChr().getName(), getChr().getId(), opqBotState,
                    getChr().getMapId(), getChr().getPosition());
            handlePQAbandoned();
            return;
        }

        // 权威对位：被游戏传到意料之外的地图时，按地图把状态拉回正确相位
        OPQBotState mapDerived = detectPhaseFromMap();
        if (mapDerived != null && mapDerived != opqBotState && !inSameStageFamily(mapDerived, opqBotState)) {
            transitionTo(mapDerived, "按地图重新对位（mapId=" + getChr().getMapId() + "）");
        }

        switch (opqBotState) {
            case RESET -> {
                resetOPQBotState();
                enterLobbyIfNeeded();
                transitionTo(OPQBotState.RECRUITMENT, "RESET 完成，开始招募");
            }
            case RECRUITMENT -> handleRecruitment();
            case IN_PARTY_IDLE -> handleInPartyIdle();
            case STAGE_1_NAVIGATE -> handleStage1Navigate();
            case STAGE_1_HIT_REACTOR -> hitReactor4Times();
            case STAGE_1_LOOT -> handleStage1Loot();
            case STAGE_1_RETURN -> handleStage1Return();
            case STAGE_1_DROP_ITEMS -> handleStage1DropItems();
            case STAGE_1_WAIT -> handleStage1Wait();
            case STAGE_1_TRANSITION -> handleStage1Transition();
            case STAGE_1_TRANSITION_PT_2 -> handleStage1TransitionPart2();
            case STAGE_2_NAVIGATE -> handleStage2Navigate();
            case STAGE_2_HIT_BOX -> hitBox4Times();
            case STAGE_2_LOOT -> handleStage2Loot();
            case STAGE_2_RETURN -> handleStage2Return();
            case STAGE_2_DROP_ITEMS -> handleStage2DropItems();
            case STAGE_2_WAIT -> handleStage2Wait();
            case EXIT_DETECT -> handleExitDetect();
            case EXIT_LOBBY -> handleExitLobby();
            case LOOP_CHECK -> handleLoopCheck();
            default -> {
                log.warn("[OPQBot {}] 未知状态 {}，停机", getChr().getName(), opqBotState);
                setRunning(false);
                orchestrator.unregisterBot(this);
            }
        }
    }

    /**
     * 不在招募大厅就自己走过去 —— 机器人上线时可能被扔在别的地图。
     */
    private void enterLobbyIfNeeded() {
        if (isInParty() || getChr().getMapId() == OPQConstants.OPQ_LOBBY) {
            return;
        }
        BotGeneration.warpBotToLocation(getChr(), OPQConstants.OPQ_LOBBY, station);
    }

    // =========================================================================
    // 招募（大厅）—— 本项目自定义：站着喊话
    // =========================================================================

    private void handleRecruitment() {
        boolean accepted = BotPartyLogic.checkPartyQueue(getChr());
        if (accepted) {
            log.debug("[OPQBot {}] 接受了组队邀请", getChr().getName());
        }
        if (isInParty()) {
            orchestrator.noteLeaderFromBot(this);
            sharedContextTrySetPhase(OPQPhase.IN_PARTY_IDLE);
            transitionTo(OPQBotState.IN_PARTY_IDLE, "已入队");
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRecruitMessageAt >= OPQConstants.RECRUIT_MESSAGE_INTERVAL_MS) {
            String msg = OPQRecruitMessages.generateRecruitMessage(getChr());
            BotActions.speak(getChr(), msg);
            lastRecruitMessageAt = now;
            // 周期性日志，降到 debug 避免刷屏（root 是 INFO，默认不输出）
            log.debug("[OPQBot {}] 招募喊话：{}", getChr().getName(), msg);
        }
        // 喊完话不挪窝：大厅里一律站着，只有被撞开、被传送带偏时才走回档案里的坐标
        standAtStation();
    }

    /**
     * 站回 web 端给这台机器人配的 spawn_x。
     *
     * <p>只比较 x，y 不参与判定；纵向沿用脚下地面的高度，目标点写成
     * {@code new Point(station.x, here.y)} 让 dy 恒为 0，从数据上就无法触发跳跃。
     */
    private void standAtStation() {
        Point here = getChr().getPosition();
        if (Math.abs(here.x - station.x) <= OPQConstants.LOBBY_STATION_TOL_X_PX) {
            return;
        }
        BotNavigator.walkTo(getChr(), new Point(station.x, here.y), false);
    }

    // =========================================================================
    // 在队里，等队长开局
    // =========================================================================

    private void handleInPartyIdle() {
        if (!isInParty()) {
            transitionTo(OPQBotState.RECRUITMENT, "开局前队伍散了");
            return;
        }
        Character leader = getPartyLeader();
        if (leader == null) {
            return;
        }
        int leaderMap = leader.getMapId();
        if (leaderMap == OPQConstants.OPQ_STAGE_1) {
            // 等队长真正落地再跟：上游这里是 blockingSleep(3000)，会把这台机器人整帧卡死 3 秒；
            // 改成跨帧计时，等待时长一样但帧不阻塞。
            long now = System.currentTimeMillis();
            if (stage1EntrySeenAt == 0) {
                stage1EntrySeenAt = now;
                return;
            }
            if (now - stage1EntrySeenAt < OPQConstants.STAGE_1_ENTRY_DELAY_MS) {
                return;
            }
            stage1EntrySeenAt = 0;
            cloudPiecesLooted = 0;
            lootedRecordItemId = -1;
            orchestrator.followLeaderWarp(getChr(), STAGE_1_ENTRY_TP);
            transitionTo(OPQBotState.STAGE_1_NAVIGATE, "跟队长进第一阶段");
            return;
        }
        stage1EntrySeenAt = 0;
        if (leaderMap == OPQConstants.OPQ_STAGE_2) {
            // 队长已经跳过第一阶段（比如重进），直接对位第二阶段
            orchestrator.followLeaderWarp(getChr(), new Point(-113, -321));
            transitionTo(OPQBotState.STAGE_2_NAVIGATE, "队长已在第二阶段");
        }
    }

    // =========================================================================
    // 第一阶段：云片
    // =========================================================================

    /**
     * 第一阶段有 20 朵云，一台机器人一轮打不完。只要还有没被别的机器人认领的活云，
     * 就把已经打碎的认领清掉、回 NAVIGATE 重新挑最近的一朵；一朵都不剩了才回去交件。
     */
    private void handleStage1Navigate() {
        sharedContextTrySetPhase(OPQPhase.STAGE_1);

        Integer reactorOid = orchestrator.assignCloudReactor(this);
        boolean moreCloudsLeft = orchestrator.hasUnclaimedLiveCloudReactor(getChr().getMap(), getChr().getId());
        if (!moreCloudsLeft) {
            sharedContext.putCloudAssignment(getChr().getId(), null);
            transitionTo(OPQBotState.STAGE_1_RETURN, "云朵全清了，回去交件");
            return;
        }

        Reactor reactor = reactorOid == null ? null : getChr().getMap().getReactorByOid(reactorOid);
        if (reactor == null || !reactor.isAlive() || reactor.getState() >= 4) {
            sharedContext.putCloudAssignment(getChr().getId(), null);
            return;
        }

        Point reactorPos = reactor.getPosition();
        Point botPos = getChr().getPosition();
        double dx = Math.abs(botPos.getX() - reactorPos.getX());
        double dy = Math.abs(botPos.getY() - reactorPos.getY());
        // 上游只比 dx。但 OPQ 第一阶段是一座纵向堆叠的塔，云朵的 x 几乎都在同一列，
        // 机器人站在入口 (266,96) 时 dx 就已经 < 200 → 直接判「到了」→ 对着空气挥 4 下
        // 把云打碎，这就是「云片不打就自己爆」。这里补上纵向判定：必须真的在云朵脚下的
        // 那层平台上（云朵离地最多约 150px，容差 200 够用）。
        if (dx <= OPQConstants.REACTOR_HIT_RANGE_PX && dy <= OPQConstants.REACTOR_HIT_RANGE_PX) {
            reactorHitsThisTarget = 0;
            transitionTo(OPQBotState.STAGE_1_HIT_REACTOR,
                    "到云朵旁边了（dx=" + (int) dx + "px, dy=" + (int) dy + "px）");
            return;
        }
        // 上游关键一步：云朵是悬空的，先把它的坐标 snap 到脚下最近的地面点再走过去，
        // 所以机器人是「站在云朵正下方」挥击，而不是飘到半空里。
        if (MovementCommands.pathFinderBetaAerial(getChr(), reactorPos) == null) {
            // 连放宽后的容差都吸附不到地面（录制数据缺失/坐标太远）：退化成「朝云朵的 x 走」，
            // 至少别让机器人一辈子卡在 NAVIGATE。
            MovementCommands.pathFinderBeta(getChr(), new Point(reactorPos.x, botPos.y));
        }
        waitFor(OPQConstants.NAVIGATE_SETTLE_MS);
    }

    private void hitReactor4Times() {
        // 先转身对着云朵：挥击动画朝角色正面播，背对着挥动作一样有，但看不出是在打云
        Integer cloudOid = sharedContext.getMyCloudAssignment(getChr().getId());
        faceReactor(cloudOid == null ? null : getChr().getMap().getReactorByOid(cloudOid));

        // 挥击打成一串；一旦状态离开 HIT 状态，剩下的挥击会被 stopUnless 掐掉
        BotTiming.Chain chain = BotTiming.chain()
                .stopUnless(() -> opqBotState == OPQBotState.STAGE_1_HIT_REACTOR);
        for (int i = 0; i < OPQConstants.MAX_REACTOR_HITS; i++) {
            chain.run(this::handleStage1HitReactor).pause(OPQConstants.SWING_INTERVAL_MS);
        }
        chain.start();
        waitFor(OPQConstants.SWING_INTERVAL_MS * OPQConstants.MAX_REACTOR_HITS + 200);
    }

    /**
     * 把朝向掰到反应堆那一侧（原地转身，靠 {@code map0/turnaroundto*} 录制回放实现）。
     *
     * <p>只转一次、且已经朝着就什么都不做 —— {@code microTurnAround*} 内部也会判朝向，
     * 这里先判省掉一次录制文件读取。
     */
    private void faceReactor(Reactor reactor) {
        if (reactor == null) {
            return;
        }
        boolean targetIsLeft = reactor.getPosition().x < getChr().getPosition().x;
        if (targetIsLeft && !MovementCommands.facingLeft(getChr())) {
            MovementCommands.microTurnAroundToLeft(getChr());
        } else if (!targetIsLeft && MovementCommands.facingLeft(getChr())) {
            MovementCommands.microTurnAroundToRight(getChr());
        }
    }

    private void handleStage1HitReactor() {
        Integer reactorOid = sharedContext.getMyCloudAssignment(getChr().getId());
        if (reactorOid == null) {
            transitionTo(OPQBotState.STAGE_1_NAVIGATE, "HIT 状态里没有分配");
            return;
        }
        Reactor reactor = getChr().getMap().getReactorByOid(reactorOid);
        if (reactor == null || !reactor.isAlive() || reactor.getState() >= 4) {
            transitionTo(OPQBotState.STAGE_1_LOOT, "到了发现云朵已经被打碎");
            return;
        }

        BotAttack.basicSwing(getChr());
        CustomReactor.hitReactor(getChr().getMap(), reactorOid);
        reactorHitsThisTarget++;

        // 机器人没有客户端，反应堆的正常掉落链路跑不起来，这最后一击由我们自己补掉落
        byte stateAfter = reactor.getState();
        if (stateAfter >= 4) {
            CustomReactor.dropItemAtReactor(getChr().getMap(), reactorOid,
                    OPQConstants.CLOUD_PIECE, getChr());
        }

        if (stateAfter >= 4 || reactorHitsThisTarget >= OPQConstants.MAX_REACTOR_HITS) {
            transitionTo(OPQBotState.STAGE_1_LOOT,
                    "云朵碎了（state=" + stateAfter + "，hits=" + reactorHitsThisTarget + "）");
        }
    }

    private void handleStage1Loot() {
        Point botPos = getChr().getPosition();
        Integer reactorOid = sharedContext.getMyCloudAssignment(getChr().getId());
        Reactor reactor = reactorOid == null ? null : getChr().getMap().getReactorByOid(reactorOid);
        Point reactorPos = reactor == null ? null : reactor.getPosition();
        int[] cloudFilter = {OPQConstants.CLOUD_PIECE};

        // 1) 先在自己脚下扫（刚打碎的云朵就在这儿）
        List<MapObject> found = BotLogic.checkForItemsOnFloor(getChr(), botPos,
                OPQConstants.STAGE_1_LOOT_SCAN_RANGE_PX, cloudFilter);

        // 2) 云片可能穿过几层地板掉下去了：从云朵的 x 往下逐段探测，命中即停
        if (found.isEmpty() && reactorPos != null) {
            for (int step = 1; step <= OPQConstants.STAGE_1_LOOT_FALLBACK_STEPS; step++) {
                Point probe = new Point(reactorPos.x,
                        reactorPos.y + step * OPQConstants.STAGE_1_LOOT_FALLBACK_STEP_PX);
                List<MapObject> hits = BotLogic.checkForItemsOnFloor(getChr(), probe,
                        OPQConstants.STAGE_1_LOOT_SCAN_RANGE_PX, cloudFilter);
                if (!hits.isEmpty()) {
                    found = hits;
                    break;
                }
            }
        }

        if (!found.isEmpty()) {
            // 按「实际捡到的片数」累加：一叠云片可能是多个（比如整图扫描时把别人打碎的也捡了），
            // 累加 1 会让交件数量又和手上真实数量脱节。
            int gained = 0;
            for (MapObject obj : found) {
                if (obj instanceof MapItem mapItem) {
                    gained += mapItem.getItem().getQuantity();
                }
            }
            DropCommands.lootItemListOnFloor(getChr(), found);
            cloudPiecesLooted += Math.max(1, gained);
            BotActions.bubble(getChr(), "云片：" + cloudPiecesLooted);
        }
        waitFor(800);

        transitionTo(OPQBotState.STAGE_1_NAVIGATE, "捡完一轮，找下一朵云");
    }

    private void handleStage1Return() {
        Point dropPoint = OPQConstants.STAGE_1_DROP_POINT;
        double dist = getChr().getPosition().distance(dropPoint);

        if (dist > OPQConstants.STAGE_1_DROP_TOL_PX) {
            if (stage1ReturnTries < OPQConstants.STAGE_1_RETURN_MAX_TRIES) {
                stage1ReturnTries++;
                // pathFinderBeta 是阻塞的：整段路走完才返回，所以这里能直接量到结果
                MovementCommands.pathFinderBeta(getChr(), dropPoint);
                waitFor(OPQConstants.NAVIGATE_SETTLE_MS);

                double after = getChr().getPosition().distance(dropPoint);
                if (after > OPQConstants.STAGE_1_DROP_TOL_PX) {
                    // 还没站到交付点：留在 RETURN 继续补走，绝不提前进 DROP_ITEMS，
                    // 否则云片会丢在半路的塔上，真人根本捡不到。
                    log.info("[OPQBot {}] 回交付点未到位（dist {} -> {}），下一帧继续走",
                            getChr().getName(), (int) dist, (int) after);
                    return;
                }
                dist = after;
            } else {
                // 走了 N 次还到不了（寻路数据缺失 / 被地形卡住）：直接送到交付点再丢。
                // 不能「人还在塔上、云片凭空出现在地下」—— 那正是「没走到位就扔」的观感来源。
                log.warn("[OPQBot {}] 回交付点超时（dist={}），传送到交付点后再丢",
                        getChr().getName(), (int) dist);
                BotGeneration.warpBotToLocation(getChr(), getChr().getMapId(), dropPoint);
                waitFor(OPQConstants.NAVIGATE_SETTLE_MS);
                dist = getChr().getPosition().distance(dropPoint);
            }
        }

        stage1ReturnTries = 0;
        transitionTo(OPQBotState.STAGE_1_DROP_ITEMS, "到了交付点（dist=" + (int) dist + "）");
    }

    /**
     * 把这一轮捡到的云片整叠丢在交付点，由真人玩家捡起来自己去祭坛交付。
     *
     * <p>与上游一致：丢在<b>自己脚下</b>、按这一轮实际捡到的片数丢。机器人不碰祭坛 ——
     * 祭坛只认「一叠恰好 20 个」，代交、凑数都不是原版行为。
     */
    private void handleStage1DropItems() {
        int cloudCount = cloudPiecesLooted;
        Point dropAt = new Point(getChr().getPosition());
        log.debug("[OPQBot {}] 在交付点 {} 丢云片 {} 片（距目标点 {}px）",
                getChr().getName(), dropAt, cloudCount,
                (int) dropAt.distance(OPQConstants.STAGE_1_DROP_POINT));

        if (cloudCount > 0) {
            BotActions.speak(getChr(), "云片 " + cloudCount + " 片，放这儿了");
            BotTiming.after(400, () ->
                    DropCommands.botThrowItemQty(getChr(), OPQConstants.CLOUD_PIECE, cloudCount, dropAt));
            waitFor(800); // 等这一叠落地再进 WAIT
        } else {
            waitFor(300);
        }

        cloudPiecesLooted = 0;
        sharedContext.markTaskComplete(getChr().getId());
        startStageWaitTimer();
        transitionTo(OPQBotState.STAGE_1_WAIT, "云片已丢在交付点，等真人交祭坛");
    }

    private void handleStage1Wait() {
        if (sharedContext.isStage1Complete() && orchestrator.isChamberlainSpawned()) {
            orchestrator.followLeaderWarp(getChr(), STAGE_1_COMPLETE_TP);
            // 走到传送门
            MovementCommands.moveToPortal(getChr(), 4);
            transitionTo(OPQBotState.STAGE_1_TRANSITION, "第一阶段完成，跟队长上塔");
            return;
        }
        if (waitTimedOut()) {
            transitionTo(OPQBotState.LOOP_CHECK,
                    "第一阶段等待超时 " + OPQConstants.STAGE_WAIT_TIMEOUT_MS + "ms");
        }
    }

    private void handleStage1Transition() {
        Character leader = getPartyLeader();
        if (leader != null && leader.getMapId() == OPQConstants.OPQ_TOWER) {
            // 上游这里是刻意同步的换图编排（落地→走门），保留 blockingSleep
            BotHelpers.blockingSleep(1000);
            orchestrator.followLeaderWarp(getChr(), new Point(-260, -32));
            BotHelpers.blockingSleep(1000);
            MovementCommands.pathFinderBeta(getChr(), new Point(159, -32));
            transitionTo(OPQBotState.STAGE_1_TRANSITION_PT_2, "在塔里等队长进第二阶段");
        }
    }

    private void handleStage1TransitionPart2() {
        Character leader = getPartyLeader();
        if (leader != null && leader.getMapId() == OPQConstants.OPQ_STAGE_2) {
            orchestrator.followLeaderWarp(getChr(), new Point(-113, -321));
            waitFor(1000);
            transitionTo(OPQBotState.STAGE_2_NAVIGATE, "到达第二阶段地图");
        }
    }

    // =========================================================================
    // 第二阶段：唱片箱
    // =========================================================================

    private void handleStage2Navigate() {
        sharedContextTrySetPhase(OPQPhase.STAGE_2);

        if (leaderLeftStage2()) {
            sharedContext.putBoxAssignment(getChr().getId(), null);
            followLeaderOut();
            return;
        }

        Integer reactorOid = orchestrator.assignBoxReactor(this);
        boolean moreBoxesLeft = orchestrator.hasUnclaimedLiveBoxReactor(getChr().getMap(), getChr().getId());
        if (!moreBoxesLeft && reactorOid == null) {
            if (lootedRecordItemId > 0) {
                transitionTo(OPQBotState.STAGE_2_RETURN, "箱子没了，手上还有唱片，先回去交");
            } else {
                sharedContext.markTaskComplete(getChr().getId());
                startStageWaitTimer();
                transitionTo(OPQBotState.STAGE_2_WAIT, "箱子没了也没东西可交，等第二阶段结束");
            }
            return;
        }

        if (reactorOid == null) {
            return;
        }

        Reactor reactor = getChr().getMap().getReactorByOid(reactorOid);
        if (reactor == null || !reactor.isAlive() || reactor.getState() >= 4) {
            sharedContext.putBoxAssignment(getChr().getId(), null);
            return;
        }

        String ordinal = orchestrator.getBoxOrdinal(reactorOid);
        BotActions.speak(getChr(), "我去开 " + ordinal + " 箱子！");

        Point reactorPos = reactor.getPosition();
        Point botPos = getChr().getPosition();
        double dx = Math.abs(botPos.getX() - reactorPos.getX());
        double dy = Math.abs(botPos.getY() - reactorPos.getY());
        // 与第一阶段同理：只比 dx 会站在塔的另一层「隔空开箱」
        if (dx <= OPQConstants.REACTOR_HIT_RANGE_PX && dy <= OPQConstants.REACTOR_HIT_RANGE_PX) {
            reactorHitsThisTarget = 0;
            transitionTo(OPQBotState.STAGE_2_HIT_BOX,
                    "到 " + ordinal + " 箱子旁边了（dx=" + (int) dx + "px, dy=" + (int) dy + "px）");
            return;
        }
        if (MovementCommands.pathFinderBetaAerial(getChr(), reactorPos) == null) {
            // 同上：吸附不到地面时退化成横着走，避免永久卡住
            MovementCommands.pathFinderBeta(getChr(), new Point(reactorPos.x, botPos.y));
        }
        waitFor(OPQConstants.NAVIGATE_SETTLE_MS);
    }

    private void hitBox4Times() {
        // 先转向箱子，否则挥击动画朝反方向
        Integer boxOid = sharedContext.getMyBoxAssignment(getChr().getId());
        faceReactor(boxOid == null ? null : getChr().getMap().getReactorByOid(boxOid));

        BotTiming.Chain chain = BotTiming.chain()
                .stopUnless(() -> opqBotState == OPQBotState.STAGE_2_HIT_BOX);
        for (int i = 0; i < OPQConstants.MAX_REACTOR_HITS; i++) {
            chain.run(this::handleStage2HitBox).pause(OPQConstants.SWING_INTERVAL_MS);
        }
        chain.start();
        waitFor(OPQConstants.SWING_INTERVAL_MS * OPQConstants.MAX_REACTOR_HITS + 200);
    }

    private void handleStage2HitBox() {
        Integer reactorOid = sharedContext.getMyBoxAssignment(getChr().getId());
        if (reactorOid == null) {
            transitionTo(OPQBotState.STAGE_2_NAVIGATE, "HIT 状态里没有箱子分配");
            return;
        }
        Reactor reactor = getChr().getMap().getReactorByOid(reactorOid);
        if (reactor == null || !reactor.isAlive() || reactor.getState() >= 4) {
            transitionTo(OPQBotState.STAGE_2_LOOT, "到了发现箱子已经开了");
            return;
        }

        BotAttack.basicSwing(getChr());
        CustomReactor.hitReactor(getChr().getMap(), reactorOid);
        reactorHitsThisTarget++;

        byte stateAfter = reactor.getState();
        if (stateAfter >= 4) {
            int recordItem = orchestrator.getBoxItemId(reactorOid);
            CustomReactor.dropItemAtReactor(getChr().getMap(), reactorOid, recordItem, getChr());
            lootedRecordItemId = recordItem;
        }

        if (stateAfter >= 4 || reactorHitsThisTarget >= OPQConstants.MAX_REACTOR_HITS) {
            transitionTo(OPQBotState.STAGE_2_LOOT,
                    "箱子开了（state=" + stateAfter + "，hits=" + reactorHitsThisTarget + "）");
        }
    }

    private void handleStage2Loot() {
        Point botPos = getChr().getPosition();
        Integer reactorOid = sharedContext.getMyBoxAssignment(getChr().getId());
        Reactor reactor = reactorOid == null ? null : getChr().getMap().getReactorByOid(reactorOid);

        int[] recordFilter = OPQConstants.STAGE_2_ITEMS.stream().mapToInt(Integer::intValue).toArray();
        // 先在自己脚下扫：唱片落在箱子正下方的地面，而机器人就是被 pathFinderBetaAerial
        // snap 到那个点站着的。扫不到再退回箱子坐标（箱子悬空时两者会差一层）。
        List<MapObject> found = BotLogic.checkForItemsOnFloor(getChr(), botPos,
                OPQConstants.STAGE_1_LOOT_SCAN_RANGE_PX, recordFilter);
        if (found.isEmpty() && reactor != null) {
            found = BotLogic.checkForItemsOnFloor(getChr(), reactor.getPosition(),
                    OPQConstants.STAGE_1_LOOT_SCAN_RANGE_PX, recordFilter);
        }
        if (!found.isEmpty()) {
            DropCommands.lootItemListOnFloor(getChr(), found);
        }

        if (lootedRecordItemId > 0) {
            BotActions.bubble(getChr(), "捡到唱片了！");
        }
        waitFor(800);

        sharedContext.putBoxAssignment(getChr().getId(), null);
        transitionTo(OPQBotState.STAGE_2_RETURN, "捡完，回音乐盒交件");
    }

    private void handleStage2Return() {
        Point dropPoint = OPQConstants.STAGE_2_DROP_POINT;
        double dist = getChr().getPosition().distance(dropPoint);

        if (dist > OPQConstants.STAGE_2_DROP_TOL_PX) {
            if (stage2ReturnTries < OPQConstants.STAGE_2_RETURN_MAX_TRIES) {
                stage2ReturnTries++;
                // pathFinderBeta 是阻塞的：整段路走完才返回，所以这里能直接量到结果
                MovementCommands.pathFinderBeta(getChr(), dropPoint);
                waitFor(OPQConstants.NAVIGATE_SETTLE_MS);

                double after = getChr().getPosition().distance(dropPoint);
                if (after > OPQConstants.STAGE_2_DROP_TOL_PX) {
                    // 还没站到音乐盒下面：留在 RETURN 继续补走，绝不提前进 DROP_ITEMS，
                    // 否则唱片丢在半路、落在判定框外，第二阶段永远不推进。
                    log.info("[OPQBot {}] 回音乐盒未到位（dist {} -> {}），下一帧继续走",
                            getChr().getName(), (int) dist, (int) after);
                    return;
                }
                dist = after;
            } else {
                // 走了 N 次还到不了（寻路数据缺失 / 被地形卡住）：直接在音乐盒坐标交件兜底，
                // 唱片照样落在判定框内，阶段能推进。
                log.warn("[OPQBot {}] 回音乐盒超时（dist={}），直接在音乐盒坐标交件",
                        getChr().getName(), (int) dist);
            }
        }

        stage2ReturnTries = 0;
        transitionTo(OPQBotState.STAGE_2_DROP_ITEMS, "到了音乐盒交件点（dist=" + (int) dist + "）");
    }

    private void handleStage2DropItems() {
        if (lootedRecordItemId > 0) {
            BotActions.speak(getChr(), "交唱片！");
            int recordId = lootedRecordItemId;
            // 丢在音乐盒坐标而不是脚底下：即使回程差了几十像素，落点也一定在
            // react.getArea() 里（activateItemReactors 是按这个矩形判定的）。
            BotTiming.after(400, () ->
                    DropCommands.botThrowItem(getChr(), recordId, OPQConstants.STAGE_2_DROP_POINT));
            lootedRecordItemId = -1;
        }
        waitFor(1000);

        if (leaderLeftStage2()) {
            followLeaderOut();
            return;
        }

        boolean moreBoxes = orchestrator.hasUnclaimedLiveBoxReactor(getChr().getMap(), getChr().getId());
        if (moreBoxes) {
            transitionTo(OPQBotState.STAGE_2_NAVIGATE, "还有箱子，回去再开一个");
        } else {
            sharedContext.markTaskComplete(getChr().getId());
            startStageWaitTimer();
            transitionTo(OPQBotState.STAGE_2_WAIT, "箱子开完了，等第二阶段结束");
        }
    }

    private void handleStage2Wait() {
        Character leader = getPartyLeader();
        int leaderMap = leader == null ? -1 : leader.getMapId();

        if (leaderMap == OPQConstants.OPQ_EXIT_LOBBY) {
            BotHelpers.blockingSleep(1000);
            orchestrator.followLeaderWarp(getChr(), new Point(-161, 323));
            transitionTo(OPQBotState.EXIT_LOBBY, "跟队长去出口大厅");
            return;
        }
        if (leaderMap == OPQConstants.OPQ_LOBBY) {
            BotHelpers.blockingSleep(1000);
            orchestrator.followLeaderWarp(getChr(), OPQConstants.OPQ_LOBBY_FALLBACK_POS);
            transitionTo(OPQBotState.LOOP_CHECK, "队长已经回招募大厅");
            return;
        }
        if (sharedContext.isStage2Complete()) {
            transitionTo(OPQBotState.EXIT_DETECT, "第二阶段完成标记被置起");
            return;
        }
        if (waitTimedOut()) {
            transitionTo(OPQBotState.LOOP_CHECK,
                    "第二阶段等待超时 " + OPQConstants.STAGE_WAIT_TIMEOUT_MS + "ms");
        }
    }

    // =========================================================================
    // 出口
    // =========================================================================

    private void handleExitDetect() {
        sharedContextTrySetPhase(OPQPhase.EXIT);

        if (getChr().getMapId() == OPQConstants.OPQ_EXIT_LOBBY) {
            transitionTo(OPQBotState.EXIT_LOBBY, "已到出口大厅");
            return;
        }

        Character leader = getPartyLeader();
        int leaderMap = leader == null ? -1 : leader.getMapId();
        if (leaderMap == OPQConstants.OPQ_EXIT_LOBBY) {
            BotHelpers.blockingSleep(1000);
            orchestrator.followLeaderWarp(getChr(), new Point(-161, 323));
            transitionTo(OPQBotState.EXIT_LOBBY, "跟队长去出口大厅");
        } else if (leaderMap == OPQConstants.OPQ_LOBBY) {
            BotHelpers.blockingSleep(1000);
            orchestrator.followLeaderWarp(getChr(), OPQConstants.OPQ_LOBBY_FALLBACK_POS);
            transitionTo(OPQBotState.LOOP_CHECK, "跟队长回招募大厅");
        }
    }

    private void handleExitLobby() {
        Character leader = getPartyLeader();
        if (leader != null && leader.getMapId() == OPQConstants.OPQ_LOBBY) {
            BotHelpers.blockingSleep(300);
            BotGeneration.warpBotToLocation(getChr(), OPQConstants.OPQ_LOBBY, station);
            transitionTo(OPQBotState.LOOP_CHECK, "跟队长回招募大厅");
            return;
        }
        // 队长没跟上来就自己回大厅，回到自己的站位
        BotHelpers.blockingSleep(500);
        BotGeneration.warpBotToLocation(getChr(), OPQConstants.OPQ_LOBBY, station);
        BotHelpers.blockingSleep(2000);
        transitionTo(OPQBotState.LOOP_CHECK, "出口流程结束，已回招募大厅");
    }

    private void handleLoopCheck() {
        // 清空这一轮的全部临时状态，下一轮从干净状态开始
        sharedContext.clearTaskComplete(getChr().getId());
        sharedContext.putCloudAssignment(getChr().getId(), null);
        sharedContext.putBoxAssignment(getChr().getId(), null);
        sharedContext.putPlatformAssignment(getChr().getId(), null);
        reactorHitsThisTarget = 0;
        stageWaitStartTime = 0;
        lootedRecordItemId = -1;
        cloudPiecesLooted = 0;
        stage1EntrySeenAt = 0;

        if (isInParty()) {
            transitionTo(OPQBotState.IN_PARTY_IDLE, "还在队里，准备下一轮");
        } else {
            transitionTo(OPQBotState.RECRUITMENT, "没队伍了，回大厅喊人");
        }
    }

    // =========================================================================
    // 掉队处理
    // =========================================================================

    private boolean isInsidePQ() {
        return switch (opqBotState) {
            case RESET, RECRUITMENT, IN_PARTY_IDLE, LOOP_CHECK -> false;
            default -> true;
        };
    }

    private void handlePQAbandoned() {
        int botId = getChr().getId();
        sharedContext.putCloudAssignment(botId, null);
        sharedContext.putBoxAssignment(botId, null);
        sharedContext.putPlatformAssignment(botId, null);
        sharedContext.clearTaskComplete(botId);

        BotGeneration.warpBotToLocation(getChr(), OPQConstants.OPQ_EXIT_LOBBY, new Point(-161, 323));
        BotHelpers.blockingSleep(1500);
        BotGeneration.warpBotToLocation(getChr(), OPQConstants.OPQ_LOBBY, station);

        resetOPQBotState();
        transitionTo(OPQBotState.RECRUITMENT, "队伍没了，回大厅重新喊人");
    }

    // =========================================================================
    // 感知工具
    // =========================================================================

    private OPQBotState detectPhaseFromMap() {
        int mapId = getChr().getMapId();
        if (mapId == OPQConstants.OPQ_LOBBY) {
            return isInParty() ? OPQBotState.IN_PARTY_IDLE : OPQBotState.RECRUITMENT;
        }
        if (mapId == OPQConstants.OPQ_STAGE_1) {
            return OPQBotState.STAGE_1_NAVIGATE;
        }
        if (mapId == OPQConstants.OPQ_TOWER) {
            return OPQBotState.STAGE_1_TRANSITION;
        }
        if (mapId == OPQConstants.OPQ_STAGE_2) {
            return OPQBotState.STAGE_2_NAVIGATE;
        }
        if (mapId == OPQConstants.OPQ_EXIT_LOBBY) {
            return OPQBotState.EXIT_DETECT;
        }
        return null;
    }

    /**
     * 避免同阶段内被「按地图对位」倒回 NAVIGATE。
     */
    private boolean inSameStageFamily(OPQBotState a, OPQBotState b) {
        return family(a) != null && family(a).equals(family(b));
    }

    private String family(OPQBotState s) {
        if (s == null) {
            return null;
        }
        String n = s.name();
        if (n.startsWith("STAGE_1")) {
            return "S1";
        }
        if (n.startsWith("STAGE_2")) {
            return "S2";
        }
        if (n.startsWith("EXIT")) {
            return "EX";
        }
        if (n.equals("RECRUITMENT") || n.equals("IN_PARTY_IDLE")) {
            return "LOBBY";
        }
        return null;
    }

    private boolean isInParty() {
        return getChr().getParty() != null;
    }

    private Character getPartyLeader() {
        if (getChr().getParty() == null || getChr().getParty().getLeader() == null) {
            return null;
        }
        return getChr().getParty().getLeader().getPlayer();
    }

    private boolean leaderLeftStage2() {
        Character leader = getPartyLeader();
        if (leader == null) {
            return false;
        }
        int leaderMap = leader.getMapId();
        return leaderMap == OPQConstants.OPQ_EXIT_LOBBY || leaderMap == OPQConstants.OPQ_LOBBY;
    }

    private void followLeaderOut() {
        Character leader = getPartyLeader();
        int leaderMap = leader == null ? -1 : leader.getMapId();
        BotHelpers.blockingSleep(1000);
        if (leaderMap == OPQConstants.OPQ_EXIT_LOBBY) {
            orchestrator.followLeaderWarp(getChr(), new Point(-161, 323));
            transitionTo(OPQBotState.EXIT_LOBBY, "队长离开第二阶段，跟到出口大厅");
        } else {
            orchestrator.followLeaderWarp(getChr(), OPQConstants.OPQ_LOBBY_FALLBACK_POS);
            transitionTo(OPQBotState.LOOP_CHECK, "队长已回招募大厅，跳过出口");
        }
    }

    private void startStageWaitTimer() {
        stageWaitStartTime = System.currentTimeMillis();
    }

    private boolean waitTimedOut() {
        return stageWaitStartTime > 0
                && (System.currentTimeMillis() - stageWaitStartTime) > OPQConstants.STAGE_WAIT_TIMEOUT_MS;
    }

    private void sharedContextTrySetPhase(OPQPhase phase) {
        if (sharedContext.getCurrentPhase() != phase) {
            orchestrator.mirrorPhase(phase);
        }
    }
}
