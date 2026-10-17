package org.gms.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gms.client.Character;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.ItemFactory;
import org.gms.dao.entity.BotProfileDO;
import org.gms.dao.entity.CharactersDO;
import org.gms.dao.entity.InventoryitemsDO;
import org.gms.dao.mapper.BotProfileMapper;
import org.gms.dao.mapper.CharactersMapper;
import org.gms.dao.mapper.InventoryequipmentMapper;
import org.gms.dao.mapper.InventoryitemsMapper;
import org.gms.exception.BizException;
import org.gms.model.dto.BotCreateDTO;
import org.gms.model.dto.BotProfileDTO;
import org.gms.model.dto.BotUpdateDTO;
import org.gms.net.server.Server;
import org.gms.soloMapling.BotCharacterFactory;
import org.gms.soloMapling.BotClient;
import org.gms.soloMapling.BotInventorySaver;
import org.gms.soloMapling.ArtificialPlayer.BotGeneration;
import org.gms.soloMapling.ArtificialPlayer.BotTier;
import org.gms.soloMapling.ArtificialPlayer.BotTierStore;
import org.gms.soloMapling.ArtificialPlayer.BotType;
import org.gms.soloMapling.ArtificialPlayer.BotDecoratorSystem.BotDecorate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.gms.dao.entity.table.BotProfileDOTableDef.BOT_PROFILE_D_O;
import static org.gms.dao.entity.table.CharactersDOTableDef.CHARACTERS_D_O;
import static org.gms.dao.entity.table.InventoryequipmentDOTableDef.INVENTORYEQUIPMENT_D_O;
import static org.gms.dao.entity.table.InventoryitemsDOTableDef.INVENTORYITEMS_D_O;

/**
 * 机器人管理服务：档案 CRUD + 上下线。
 *
 * <p>线程模型：{@link #runningBots} 只存<b>在线</b>的机器人，key 是 {@code bot_profile.id}，
 * value 是真实挂在地图上的 {@link Character}。下线就移除，所以「在线状态」不需要落库，
 * 也不会出现「库里显示在线、实际进程里没有」的假状态。
 *
 * <p>启动时（{@link ApplicationListener} 监听 {@link ApplicationReadyEvent}）做两件事：初始化无头客户端、
 * 把 {@code auto_start=1} 的机器人拉起来。任何一步失败都只记日志，不能拖垮整个服务启动。
 *
 * <p>为什么不用 {@code ApplicationRunner}：Runner 阶段各 bean 的执行顺序由 {@code @Order} 决定，
 * 而 {@link org.gms.manager.ServerManager} 没有显式 order，机器人这边一旦抢到它前面，
 * 就会在 {@code Server.init()} 还没建好世界时就并发访问 {@code Server} 单例（曾因此触发
 * 「两个 Server 实例 → 大区初始化 NPE」）。{@link ApplicationReadyEvent} 在所有 Runner 之后发布，
 * 此时世界与频道必定已经就绪。
 *
 * @author WorkBuddy
 * @since 2026-10-17
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BotService implements ApplicationListener<ApplicationReadyEvent> {

    /**
     * 机器人统一挂在 0 世界 1 频道（与 SoloMapling 的 WORLD_SCANIA / CHANNEL_1 一致）
     */
    public static final int BOT_WORLD = 0;
    public static final int BOT_CHANNEL = 1;

    /**
     * 默认出生地图：自由市场入口
     */
    public static final int DEFAULT_MAP_ID = 910000000;

    private final BotProfileMapper botProfileMapper;
    private final CharactersMapper charactersMapper;
    private final InventoryitemsMapper inventoryitemsMapper;
    private final InventoryequipmentMapper inventoryequipmentMapper;
    private final BotCharacterFactory botCharacterFactory;

    /**
     * 在线机器人：bot_profile.id → Character
     */
    private final Map<Integer, Character> runningBots = new ConcurrentHashMap<>();

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        try {
            BotGeneration.init(BOT_WORLD, BOT_CHANNEL);
        } catch (Exception e) {
            log.error("[BotService] 无头客户端初始化失败，本轮不自动上线机器人", e);
            return;
        }

        // 走到这里世界/频道已经建好（ApplicationReadyEvent 在所有 Runner 之后），
        // 但仍放后台线程里做：机器人多了以后 spawn 会做建号、落库、装饰，不该卡住启动线程。
        // autoStartWhenReady 里的轮询保留作兜底，正常情况第一轮就通过。
        Thread t = new Thread(this::autoStartWhenReady, "bot-auto-start");
        t.setDaemon(true);
        t.start();
    }

    private void autoStartWhenReady() {
        for (int i = 0; i < 60; i++) {
            try {
                if (Server.getInstance().getChannel(BOT_WORLD, BOT_CHANNEL) != null
                        && Server.getInstance().getWorld(BOT_WORLD) != null) {
                    break;
                }
            } catch (Exception ignored) {
                // 世界还没初始化完，继续等
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        List<BotProfileDO> autoStart;
        try {
            autoStart = botProfileMapper.selectListByQuery(
                    QueryWrapper.create().where(BOT_PROFILE_D_O.AUTO_START.eq(true)));
        } catch (Exception e) {
            log.error("[BotService] 读取自动上线列表失败", e);
            return;
        }
        if (autoStart.isEmpty()) {
            log.info("[BotService] 没有 auto_start 的机器人");
            return;
        }
        int ok = 0;
        for (BotProfileDO profile : autoStart) {
            try {
                spawnOne(profile);
                ok++;
            } catch (Exception e) {
                log.error("[BotService] 自动上线机器人 {} 失败", profile.getId(), e);
            }
        }
        log.info("[BotService] 自动上线完成：{}/{}", ok, autoStart.size());
    }

    // ------------------------------------------------------------------ 查询

    public Page<BotProfileDTO> list(Integer page, Integer size, String name) {
        QueryWrapper qw = QueryWrapper.create();
        if (name != null && !name.isBlank()) {
            List<CharactersDO> matched = charactersMapper.selectListByQuery(
                    QueryWrapper.create().where(CHARACTERS_D_O.NAME.like(name.trim())));
            List<Integer> cids = matched.stream().map(CharactersDO::getId).toList();
            if (cids.isEmpty()) {
                return new Page<>();
            }
            qw.where(BOT_PROFILE_D_O.CHARACTER_ID.in(cids));
        }
        qw.orderBy(BOT_PROFILE_D_O.ID.asc());

        Page<BotProfileDO> raw = botProfileMapper.paginate(page == null ? 1 : page,
                size == null ? 20 : size, qw);
        Page<BotProfileDTO> result = new Page<>();
        result.setPageNumber(raw.getPageNumber());
        result.setPageSize(raw.getPageSize());
        result.setTotalPage(raw.getTotalPage());
        result.setTotalRow(raw.getTotalRow());
        result.setRecords(toDtoList(raw.getRecords()));
        return result;
    }

    private List<BotProfileDTO> toDtoList(List<BotProfileDO> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return Collections.emptyList();
        }
        List<Integer> cids = profiles.stream().map(BotProfileDO::getCharacterId).toList();
        Map<Integer, CharactersDO> byCid = new HashMap<>();
        for (CharactersDO c : charactersMapper.selectListByQuery(
                QueryWrapper.create().where(CHARACTERS_D_O.ID.in(cids)))) {
            byCid.put(c.getId(), c);
        }
        List<BotProfileDTO> list = new ArrayList<>(profiles.size());
        for (BotProfileDO p : profiles) {
            BotProfileDTO dto = new BotProfileDTO();
            dto.setId(p.getId());
            dto.setCharacterId(p.getCharacterId());
            dto.setAccountId(p.getAccountId());
            dto.setMapId(p.getMapId());
            dto.setSpawnX(p.getSpawnX());
            dto.setSpawnY(p.getSpawnY());
            dto.setAutoStart(p.isAutoStart());
            dto.setBotType(p.getBotType());
            dto.setNote(p.getNote());
            dto.setCreateTime(p.getCreateTime());
            dto.setUpdateTime(p.getUpdateTime());
            dto.setOnline(runningBots.containsKey(p.getId()));
            dto.setDecorated(p.isDecorated());
            dto.setBotTier(p.getBotTier());
            CharactersDO c = byCid.get(p.getCharacterId());
            if (c != null) {
                dto.setName(c.getName());
                dto.setLevel(c.getLevel());
                dto.setJob(c.getJob());
                dto.setGender(c.getGender());
            }
            list.add(dto);
        }
        return list;
    }

    // ------------------------------------------------------------------ 新建 / 修改 / 删除

    @Transactional(rollbackFor = Exception.class)
    public void create(BotCreateDTO dto) {
        BotGeneration.init(BOT_WORLD, BOT_CHANNEL);
        BotClient client = BotGeneration.getBotClient();

        int mapId = dto.getMapId() == null ? DEFAULT_MAP_ID : dto.getMapId();
        BotCharacterFactory.Created created = botCharacterFactory.createBotCharacter(
                dto.getName(), dto.getLevel(), dto.getJob(), dto.getGender(), mapId, client);

        BotProfileDO profile = BotProfileDO.builder()
                .characterId(created.characterId())
                .accountId(created.accountId())
                .mapId(mapId)
                .spawnX(dto.getSpawnX() == null ? 0 : dto.getSpawnX())
                .spawnY(dto.getSpawnY() == null ? 0 : dto.getSpawnY())
                .autoStart(Boolean.TRUE.equals(dto.getAutoStart()))
                .botType(resolveBotType(dto.getBotType()))
                .note(dto.getNote() == null ? "" : dto.getNote())
                .build();
        botProfileMapper.insertSelective(profile);

        if (Boolean.TRUE.equals(dto.getAutoStart())) {
            // 首次上线会随机生成外观/装备并写 inventoryitems（独立连接、不在本事务里）。
            // 放到提交之后执行，避免事务回滚时留下「有装备没档案」的孤儿物品行。
            spawnAfterCommit(profile);
        }
    }

    /**
     * 校验并归一化机器人类型码：留空回落 {@code IDLE}，未知码直接拒绝，
     * 避免脏值落库后前端只能显示原始码。
     */
    private String resolveBotType(String code) {
        if (code != null && !code.isBlank() && !BotType.isValid(code)) {
            throw BizException.illegalArgument("未知的机器人类型：" + code);
        }
        return BotType.normalize(code);
    }

    private void spawnAfterCommit(BotProfileDO profile) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        spawnOne(profile);
                    } catch (Exception e) {
                        log.error("[BotService] 机器人 {} 创建后自动上线失败（档案已创建，可手动上线）",
                                profile.getId(), e);
                    }
                }
            });
        } else {
            spawnOne(profile);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(BotUpdateDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw BizException.illegalArgument("缺少机器人 id");
        }
        BotProfileDO profile = botProfileMapper.selectOneById(dto.getId());
        if (profile == null) {
            throw BizException.illegalArgument("机器人不存在：" + dto.getId());
        }

        // 等级 / 职业是真实角色属性，改这里等于改 characters 表
        if (dto.getLevel() != null || dto.getJob() != null) {
            CharactersDO chr = charactersMapper.selectOneById(profile.getCharacterId());
            if (chr != null) {
                if (dto.getLevel() != null) chr.setLevel(dto.getLevel());
                if (dto.getJob() != null) chr.setJob(dto.getJob());
                charactersMapper.update(chr);
            }
        }

        if (dto.getMapId() != null) profile.setMapId(dto.getMapId());
        if (dto.getSpawnX() != null) profile.setSpawnX(dto.getSpawnX());
        if (dto.getSpawnY() != null) profile.setSpawnY(dto.getSpawnY());
        if (dto.getAutoStart() != null) profile.setAutoStart(dto.getAutoStart());
        if (dto.getBotType() != null) profile.setBotType(resolveBotType(dto.getBotType()));
        if (dto.getNote() != null) profile.setNote(dto.getNote());
        botProfileMapper.update(profile);

        // 在线的话重启一次让新坐标/等级生效（外观与装备已落库，重启不会重新随机）
        if (runningBots.containsKey(profile.getId())) {
            despawnOne(profile);
            spawnOne(profile);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Integer> ids) {
        for (Integer id : ids == null ? List.<Integer>of() : ids) {
            BotProfileDO profile = botProfileMapper.selectOneById(id);
            if (profile == null) continue;
            despawnOne(profile);
            botProfileMapper.deleteById(id);
            botCharacterFactory.deleteCharacter(profile.getCharacterId());
        }
    }

    // ------------------------------------------------------------------ 上线 / 下线

    public void spawn(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw BizException.illegalArgument("ids 不能为空");
        }
        BotGeneration.init(BOT_WORLD, BOT_CHANNEL);
        List<String> failed = new ArrayList<>();
        for (Integer id : ids) {
            BotProfileDO profile = botProfileMapper.selectOneById(id);
            if (profile == null) {
                failed.add(id + ":档案不存在");
                continue;
            }
            try {
                spawnOne(profile);
            } catch (Exception e) {
                log.error("[BotService] 上线机器人 {} 失败", id, e);
                failed.add(id + ":" + e.getMessage());
            }
        }
        if (!failed.isEmpty()) {
            throw BizException.illegalArgument("部分机器人上线失败：" + String.join("; ", failed));
        }
    }

    public void despawn(List<Integer> ids) {
        for (Integer id : ids == null ? List.<Integer>of() : ids) {
            BotProfileDO profile = botProfileMapper.selectOneById(id);
            if (profile != null) {
                despawnOne(profile);
            }
        }
    }

    private void spawnOne(BotProfileDO profile) {
        if (runningBots.containsKey(profile.getId())) {
            return;
        }
        Character bot = BotGeneration.spawnBot(profile);
        ensureDecorated(profile, bot);
        runningBots.put(profile.getId(), bot);
    }

    // ------------------------------------------------------------------ 外观 / 装备落库

    /**
     * 保证机器人「外观 + 装备」在库里存在，并且只在第一次随机。
     *
     * <p>三条分支：
     * <ul>
     *   <li>{@code decorated=true}：外观已固化。装备栏有东西就直接用（真人加载路径），
     *       空的话说明被重置过，只补装备、绝不重新捏脸；</li>
     *   <li>{@code decorated=false} 但装备栏已有物品：说明有人手工给过装备，
     *       只回填标记，不覆盖；</li>
     *   <li>其余：首次生成，外观 + 装备一起随机，然后一起写库。</li>
     * </ul>
     *
     * <p>落库失败不会阻断上线：内存里这套装备是好的，只是没持久化，
     * 记 error 并把 {@code decorated} 留在 0，下次上线重试。
     */
    private void ensureDecorated(BotProfileDO profile, Character bot) {
        boolean hasEquip = BotInventorySaver.hasEquipped(bot);

        if (profile.isDecorated()) {
            BotTierStore.setTier(bot, parseTier(profile.getBotTier()));
            if (hasEquip) {
                return;
            }
            log.info("[BotService] 机器人 {}（cid={}）外观已固化但身上没装备，只补装备",
                    bot.getName(), bot.getId());
            BotDecorate.decorateEquipsOnly(bot);
            persistItems(bot);
            return;
        }

        if (hasEquip) {
            log.info("[BotService] 机器人 {}（cid={}）库里已有装备，跳过随机生成，仅回填 decorated",
                    bot.getName(), bot.getId());
            BotTierStore.setTier(bot, parseTier(profile.getBotTier()));
            profile.setDecorated(true);
            botProfileMapper.update(profile);
            return;
        }

        BotDecorate.decorateExisting(bot);
        profile.setBotTier(BotTierStore.getTier(bot).name());
        if (persistItems(bot)) {
            persistAppearance(bot);
            profile.setDecorated(true);
        }
        botProfileMapper.update(profile);
    }

    /**
     * 写 inventoryitems / inventoryequipment。失败只记日志，返回 false 让调用方不要置 decorated。
     */
    private boolean persistItems(Character bot) {
        try {
            BotInventorySaver.saveItems(bot);
            return true;
        } catch (Exception e) {
            log.error("[BotService] 机器人 {}（cid={}）装备落库失败，本次仍允许上线；下次上线会重试",
                    bot.getName(), bot.getId(), e);
            return false;
        }
    }

    /**
     * 外观四个字段回写 characters。装饰只改这四项，其余角色字段（等级/职业/属性）建号时就定了。
     */
    private void persistAppearance(Character bot) {
        try {
            CharactersDO chr = charactersMapper.selectOneById(bot.getId());
            if (chr == null) {
                return;
            }
            chr.setGender(bot.getGender());
            chr.setHair(bot.getHair());
            chr.setFace(bot.getFace());
            chr.setSkincolor(bot.getSkinColor() == null ? null : bot.getSkinColor().getId());
            charactersMapper.update(chr);
        } catch (Exception e) {
            log.error("[BotService] 机器人 {}（cid={}）外观落库失败，下次上线外观可能重新随机",
                    bot.getName(), bot.getId(), e);
        }
    }

    private BotTier parseTier(String tierName) {
        BotTier tier = tierName == null ? null : BotTier.fromString(tierName);
        return tier == null ? BotTier.getDefaultTier() : tier;
    }

    // ------------------------------------------------------------------ 重置装备

    /**
     * 重置装备：清掉身上那一栏，重新随机一套。
     *
     * <p><b>不碰发型 / 脸 / 肤色</b> —— 外观在首次生成时已固化，重置只换穿的东西。
     *
     * <p>在线机器人立即换装（内存清了重新装饰再落库，{@code equipChanged} 会广播）；
     * 离线的只清库，下次上线走「已装饰但没装备」分支自动补一套。
     */
    public void resetEquip(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw BizException.illegalArgument("ids 不能为空");
        }
        List<String> failed = new ArrayList<>();
        for (Integer id : ids) {
            BotProfileDO profile = botProfileMapper.selectOneById(id);
            if (profile == null) {
                failed.add(id + ":档案不存在");
                continue;
            }
            try {
                Character bot = runningBots.get(id);
                if (bot != null) {
                    BotInventorySaver.clearEquippedInMemory(bot);
                    BotDecorate.decorateEquipsOnly(bot);
                    // 清空那一步刻意不广播，这里统一广播一次，避免看到一帧「裸体」
                    bot.equipChanged();
                    BotInventorySaver.saveItems(bot);
                    log.info("[BotService] 机器人 {}（cid={}）装备已重置（在线，已即时换装）",
                            bot.getName(), bot.getId());
                } else {
                    clearEquippedRows(profile.getCharacterId());
                    log.info("[BotService] 机器人档案 {}（cid={}）装备已重置（离线，下次上线补一套）",
                            id, profile.getCharacterId());
                }
            } catch (Exception e) {
                log.error("[BotService] 重置机器人 {} 装备失败", id, e);
                failed.add(id + ":" + e.getMessage());
            }
        }
        if (!failed.isEmpty()) {
            throw BizException.illegalArgument("部分机器人重置装备失败：" + String.join("; ", failed));
        }
    }

    /**
     * 删掉某个角色身上那一栏的库记录（只动 EQUIPPED，消耗/其它/现金栏不动）。
     */
    private void clearEquippedRows(Integer cid) {
        if (cid == null) {
            return;
        }
        List<InventoryitemsDO> rows = inventoryitemsMapper.selectListByQuery(QueryWrapper.create()
                .where(INVENTORYITEMS_D_O.TYPE.eq(ItemFactory.INVENTORY.getValue()))
                .and(INVENTORYITEMS_D_O.CHARACTERID.eq(cid))
                .and(INVENTORYITEMS_D_O.INVENTORYTYPE.eq((int) InventoryType.EQUIPPED.getType())));
        if (rows.isEmpty()) {
            return;
        }
        List<Long> itemIds = rows.stream().map(InventoryitemsDO::getInventoryitemid).toList();
        inventoryequipmentMapper.deleteByQuery(QueryWrapper.create()
                .where(INVENTORYEQUIPMENT_D_O.INVENTORYITEMID.in(itemIds)));
        inventoryitemsMapper.deleteByQuery(QueryWrapper.create()
                .where(INVENTORYITEMS_D_O.INVENTORYITEMID.in(itemIds)));
        log.info("[BotService] 已清除 cid={} 的 {} 件装备库记录", cid, itemIds.size());
    }

    private void despawnOne(BotProfileDO profile) {
        Character bot = runningBots.remove(profile.getId());
        if (bot != null) {
            BotGeneration.despawnBot(bot);
        }
    }

    /**
     * 进程中当前在线的机器人数（给启动日志/监控用）
     */
    public int onlineCount() {
        return runningBots.size();
    }
}
