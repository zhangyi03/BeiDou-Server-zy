package org.gms.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gms.config.BossRespawnConfigCache;
import org.gms.config.GameConfig;
import org.gms.dao.entity.BossRespawnConfigDO;
import org.gms.dao.mapper.BossRespawnConfigMapper;
import org.gms.model.dto.BossRespawnConfigDTO;
import org.gms.net.server.Server;
import org.gms.net.server.channel.Channel;
import org.gms.scripting.event.EventManager;
import org.gms.scripting.event.EventScriptManager;
import org.gms.util.I18nUtil;
import org.gms.util.RequireUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.gms.dao.entity.table.BossRespawnConfigDOTableDef.BOSS_RESPAWN_CONFIG_D_O;

/**
 * 野外Boss刷新配置服务。
 * <p>
 * 数据流向：本服务写库后立即刷新 {@link BossRespawnConfigCache}，
 * 事件脚本通过 {@code EventManager#getBossRespawnConfig()} 读缓存取得覆盖项，
 * 从而做到改数据库即控制单个Boss的刷新时间。
 * <p>
 * 让改动生效有三条路，优先级从高到低：
 * <ol>
 *   <li>调本类 {@link #updateConfig}（Web 接口），写库即刷缓存 + 重建排期，最彻底；</li>
 *   <li>直接改数据库（SQL/DB工具）：由后台定时任务 {@link #refreshIfChanged()} 在
 *       {@code BOSS_RESPAWN_CONFIG_REFRESH_INTERVAL} 内自动发现并热应用，无需重启；</li>
 *   <li>调 {@link #reloadAll()}（Web 接口 {@code /bossrespawn/v1/reload}）立即强制应用。</li>
 * </ol>
 * 注意：仅刷新缓存是不够的。已提交的定时器不会自动采用新的间隔，
 * 必须再 {@code cancelSchedule + scheduleNew} 重建排期才会立刻按新值生效。
 *
 * @author WorkBuddy
 * @since 2026-09-23
 */
@Service
@AllArgsConstructor
@Slf4j
public class BossRespawnConfigService {

    /**
     * 全局刷新时间倍率配置键，与 EventManager.getBossTime 读取的是同一个键
     */
    private static final String BOSS_RESPAWN_TIME_RATE = "boss_respawn_mob_time_rate";

    /**
     * 野外Boss事件脚本名前缀，重建排期时按它筛选，避免动到其他事件
     */
    private static final String AREA_BOSS_EVENT_PREFIX = "AreaBoss";

    private final BossRespawnConfigMapper bossRespawnConfigMapper;

    /**
     * 服务启动时把配置加载进静态缓存
     */
    public void loadConfigs() {
        BossRespawnConfigCache.initConfig(queryAsMap());
    }

    /**
     * 检测数据库是否有变化：有变化才刷新缓存并重建野外Boss排期。
     * <p>
     * 供后台定时任务调用，实现"直接改库也能自动生效、无需重启也不用打命令"。
     * 本方法自行吞掉异常，绝不向上抛，避免周期任务被单次失败打断。
     *
     * @return true 表示检测到变更并已热应用
     */
    public boolean refreshIfChanged() {
        try {
            Map<String, BossRespawnConfigDO> latest = queryAsMap();
            if (!behaviourChanged(latest)) {
                return false;
            }

            BossRespawnConfigCache.initConfig(latest);
            int rebuilt = rebuildAreaBossSchedules();
            log.info("检测到野外Boss刷新配置变更，已热应用并重建 {} 个AreaBoss排期", rebuilt);
            return true;
        } catch (Exception e) {
            log.error("检测野外Boss刷新配置变更失败", e);
            return false;
        }
    }

    /**
     * 判断库里的值相对缓存是否有"会影响行为"的变更。
     * <p>
     * 只比较脚本真正消费的那几个字段，刻意不比较 id/create_time/update_time：
     * TIMESTAMP 的读出精度（秒/微秒）在不同驱动配置下可能不一致，
     * 若把它们算进去，会出现"每轮都以为变了"，导致每一轮轮询都重建一次排期，
     * 反而把配置的间隔抹平成轮询周期（1 分钟），Boss 一被杀就立刻回来。
     */
    private boolean behaviourChanged(Map<String, BossRespawnConfigDO> latest) {
        Map<String, BossRespawnConfigDO> cached = BossRespawnConfigCache.snapshot();
        if (cached.size() != latest.size()) {
            return true;
        }

        for (Map.Entry<String, BossRespawnConfigDO> entry : latest.entrySet()) {
            BossRespawnConfigDO old = cached.get(entry.getKey());
            if (old == null || !behaviourEquals(old, entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private boolean behaviourEquals(BossRespawnConfigDO a, BossRespawnConfigDO b) {
        return Objects.equals(a.getBossId(), b.getBossId())
                && Objects.equals(a.getMapId(), b.getMapId())
                && Objects.equals(a.getBossName(), b.getBossName())
                && Objects.equals(a.getSpawnInterval(), b.getSpawnInterval())
                && Objects.equals(a.getPointX(), b.getPointX())
                && Objects.equals(a.getPointY(), b.getPointY())
                && Objects.equals(a.getNotice(), b.getNotice())
                && a.isEnabled() == b.isEnabled();
    }

    /**
     * 强制重载配置并重建野外Boss排期（Web 接口/命令立即生效用）
     */
    public void reloadAll() {
        loadConfigs();
        int rebuilt = rebuildAreaBossSchedules();
        log.info("野外Boss刷新配置已重载，重建 {} 个AreaBoss排期", rebuilt);
    }

    /**
     * 取消并重建所有 AreaBoss 事件的排期，让新间隔立即生效。
     * <p>
     * 两步走，顺序不能颠倒：
     * <ol>
     *   <li>{@link EventManager#cancelAllSchedules()} 彻底清空该事件的待执行排期。
     *       不能用脚本里的 {@code cancelSchedule()} —— 它只能取消"最近一次登记"的任务，
     *       历史上出现过的孤儿条目（重建与正在执行的排期任务交叉时产生）清不掉，
     *       会导致同一Boss并行多条定时链，表现为"改了间隔却仍按旧节奏刷"；</li>
     *   <li>再调脚本的 {@code rescheduleNew()}：它只按最新配置排下一拍，
     *       <b>不立即检查、不立即生成Boss</b>，避免"点保存就把Boss刷新了"。</li>
     * </ol>
     * 未改造的脚本没有 {@code rescheduleNew}，回落到原 {@code scheduleNew()}（0 延迟立即检查一次）。
     *
     * @return 实际重建的事件数
     */
    public int rebuildAreaBossSchedules() {
        int rebuilt = 0;
        // 清理前各事件的待执行排期总数，正常应等于事件数；偏大说明存在孤儿排期
        int pendingBefore = 0;
        for (Channel channel : Server.getInstance().getAllChannels()) {
            EventScriptManager eventSM = channel.getEventSM();
            if (eventSM == null) {
                continue;
            }
            for (EventManager eventManager : eventSM.getEventManagers()) {
                if (!eventManager.getName().startsWith(AREA_BOSS_EVENT_PREFIX)) {
                    continue;
                }
                try {
                    pendingBefore += eventManager.getPendingScheduleCount();
                    eventManager.cancelAllSchedules();
                    try {
                        eventManager.getIv().invokeFunction("rescheduleNew");
                    } catch (NoSuchMethodException noSuchMethod) {
                        // 老脚本没有 rescheduleNew，保持原行为
                        eventManager.getIv().invokeFunction("scheduleNew");
                    }
                    rebuilt++;
                } catch (Exception e) {
                    // 个别脚本异常不影响其他Boss，记录后继续
                    log.warn("重建AreaBoss排期失败: {}", eventManager.getName(), e);
                }
            }
        }
        if (rebuilt > 0) {
            log.info("重建AreaBoss排期：{} 个事件，清理前待执行排期共 {} 条", rebuilt, pendingBefore);
        }
        return rebuilt;
    }

    private Map<String, BossRespawnConfigDO> queryAsMap() {
        Map<String, BossRespawnConfigDO> configMap = new HashMap<>();
        for (BossRespawnConfigDO config : bossRespawnConfigMapper.selectAll()) {
            configMap.put(config.getEventName(), config);
        }
        return configMap;
    }

    /**
     * 获取全部刷新配置（附带实际生效分钟数，供前端展示）
     */
    public List<BossRespawnConfigDTO> getConfigList() {
        float timeRate = getTimeRate();

        List<BossRespawnConfigDTO> result = new ArrayList<>();
        for (BossRespawnConfigDO config : bossRespawnConfigMapper.selectAll()) {
            result.add(toDTO(config, timeRate));
        }
        return result;
    }

    /**
     * 更新（不存在则新增）单条刷新配置，写库后立即刷新静态缓存并重建排期
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateConfig(BossRespawnConfigDTO dto) {
        RequireUtil.requireNotEmpty(dto.getEventName(), I18nUtil.getExceptionMessage("PARAMETER_SHOULD_NOT_EMPTY", "eventName"));
        RequireUtil.requireNotNull(dto.getSpawnInterval(), I18nUtil.getExceptionMessage("PARAMETER_SHOULD_NOT_EMPTY", "spawnInterval"));
        // 坐标必须成对出现：脚本只判断 pointX 是否为空，只填 X 会生成出非法坐标
        RequireUtil.requireTrue((dto.getPointX() == null) == (dto.getPointY() == null),
                I18nUtil.getExceptionMessage("BOSS_RESPAWN_POINT_PAIR_REQUIRED"));

        String eventName = dto.getEventName();
        boolean enabled = dto.getEnabled() == null || dto.getEnabled();
        Date now = new Date();

        BossRespawnConfigDO existing = selectByEventName(eventName);
        if (existing == null) {
            BossRespawnConfigDO insertConfig = BossRespawnConfigDO.builder()
                    .eventName(eventName)
                    .bossId(dto.getBossId())
                    .mapId(dto.getMapId())
                    .bossName(dto.getBossName())
                    .spawnInterval(dto.getSpawnInterval())
                    .pointX(dto.getPointX())
                    .pointY(dto.getPointY())
                    .notice(dto.getNotice())
                    .enabled(enabled)
                    .createTime(now)
                    .updateTime(now)
                    .build();
            bossRespawnConfigMapper.insertSelective(insertConfig);
            BossRespawnConfigCache.update(eventName, insertConfig);
            log.info("新增Boss刷新配置: {} 间隔 {} 分钟", eventName, dto.getSpawnInterval());
        } else {
            // 必须用 UpdateChain 而不是 update(entity)：update 会忽略 null 字段，
            // 那样「清空坐标/公告以回落脚本默认值」这个操作就会静默失效
            UpdateChain.of(bossRespawnConfigMapper)
                    .set(BOSS_RESPAWN_CONFIG_D_O.BOSS_ID, dto.getBossId())
                    .set(BOSS_RESPAWN_CONFIG_D_O.MAP_ID, dto.getMapId())
                    .set(BOSS_RESPAWN_CONFIG_D_O.BOSS_NAME, dto.getBossName())
                    .set(BOSS_RESPAWN_CONFIG_D_O.SPAWN_INTERVAL, dto.getSpawnInterval())
                    .set(BOSS_RESPAWN_CONFIG_D_O.POINT_X, dto.getPointX())
                    .set(BOSS_RESPAWN_CONFIG_D_O.POINT_Y, dto.getPointY())
                    .set(BOSS_RESPAWN_CONFIG_D_O.NOTICE, dto.getNotice())
                    .set(BOSS_RESPAWN_CONFIG_D_O.ENABLED, enabled)
                    .where(BOSS_RESPAWN_CONFIG_D_O.EVENT_NAME.eq(eventName))
                    .update();

            // 回查一次取库里的最终值（自增id、update_time 以库为准）再刷缓存
            BossRespawnConfigCache.update(eventName, selectByEventName(eventName));
            log.info("更新Boss刷新配置: {} 间隔 {} 分钟，启用 {}", eventName, dto.getSpawnInterval(), enabled);
        }

        // 缓存已更新，但已提交的定时器还挂着旧间隔，必须重建排期才会立刻按新值生效
        rebuildAreaBossSchedules();
    }

    /**
     * 删除配置，脚本随即回落自身默认值
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteConfig(String eventName) {
        RequireUtil.requireNotEmpty(eventName, I18nUtil.getExceptionMessage("PARAMETER_SHOULD_NOT_EMPTY", "eventName"));
        RequireUtil.requireNotNull(selectByEventName(eventName), I18nUtil.getExceptionMessage("BOSS_RESPAWN_CONFIG_NOT_FOUND"));

        bossRespawnConfigMapper.deleteByQuery(QueryWrapper.create().where(BOSS_RESPAWN_CONFIG_D_O.EVENT_NAME.eq(eventName)));
        BossRespawnConfigCache.update(eventName, null);
        log.info("删除Boss刷新配置: {}，已回落脚本默认值", eventName);

        rebuildAreaBossSchedules();
    }

    private BossRespawnConfigDO selectByEventName(String eventName) {
        return bossRespawnConfigMapper.selectOneByQuery(
                QueryWrapper.create().where(BOSS_RESPAWN_CONFIG_D_O.EVENT_NAME.eq(eventName)));
    }

    /**
     * 当前全局刷新时间倍率
     */
    private float getTimeRate() {
        return GameConfig.getServerFloat(BOSS_RESPAWN_TIME_RATE);
    }

    /**
     * 计算实际生效分钟数：间隔 × 全局倍率。
     * 间隔 <=0 表示沿用脚本常量，此处返回 null。
     */
    private Integer calcEffectiveMinutes(Integer spawnInterval, float timeRate) {
        if (spawnInterval == null || spawnInterval <= 0) {
            return null;
        }
        return Math.max(Math.round(spawnInterval * timeRate), 1);
    }

    private BossRespawnConfigDTO toDTO(BossRespawnConfigDO config, float timeRate) {
        return BossRespawnConfigDTO.builder()
                .id(config.getId())
                .eventName(config.getEventName())
                .bossId(config.getBossId())
                .mapId(config.getMapId())
                .bossName(config.getBossName())
                .spawnInterval(config.getSpawnInterval())
                .pointX(config.getPointX())
                .pointY(config.getPointY())
                .notice(config.getNotice())
                .enabled(config.isEnabled())
                .effectiveMinutes(calcEffectiveMinutes(config.getSpawnInterval(), timeRate))
                .timeRate(timeRate)
                .build();
    }
}
