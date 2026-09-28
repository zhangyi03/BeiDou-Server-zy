package org.gms.config;

import lombok.extern.slf4j.Slf4j;
import org.gms.dao.entity.BossRespawnConfigDO;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 野外Boss刷新配置静态缓存。
 * <p>
 * 为什么要静态缓存而不是走 Spring 注入：读取方 {@code EventManager} 不是 Spring Bean，
 * 事件脚本引擎也拿不到 ApplicationContext，静态入口是这条链路上唯一干净的访问方式。
 * 写入侧（BossRespawnConfigService）写库后立即刷新本缓存，做到改完即生效。
 * <p>
 * 键是事件脚本名（不带 .js 后缀），与 {@code EventManager.getName()} 一致。
 *
 * @author WorkBuddy
 * @since 2026-09-23
 */
@Slf4j
public class BossRespawnConfigCache {

    private static final Map<String, BossRespawnConfigDO> CONFIG_CACHE = new ConcurrentHashMap<>();

    private BossRespawnConfigCache() {
    }

    /**
     * 全量初始化缓存（服务启动时调用）
     */
    public static void initConfig(Map<String, BossRespawnConfigDO> configs) {
        CONFIG_CACHE.clear();
        if (configs != null) {
            CONFIG_CACHE.putAll(configs);
        }
        log.info("Loaded {} boss respawn configs", CONFIG_CACHE.size());
    }

    /**
     * 取某个事件脚本的刷新配置，无配置返回 null（脚本据此回落到自身常量）
     */
    public static BossRespawnConfigDO get(String eventName) {
        if (eventName == null) {
            return null;
        }
        return CONFIG_CACHE.get(eventName);
    }

    /**
     * 更新单个配置；config 为 null 表示移除（移除后脚本回落自身默认值）
     */
    public static void update(String eventName, BossRespawnConfigDO config) {
        if (eventName == null) {
            return;
        }
        if (config == null) {
            CONFIG_CACHE.remove(eventName);
        } else {
            CONFIG_CACHE.put(eventName, config);
        }
    }

    /**
     * 当前已缓存的全部配置
     */
    public static Collection<BossRespawnConfigDO> getAll() {
        return Collections.unmodifiableCollection(CONFIG_CACHE.values());
    }

    /**
     * 已缓存的配置条数
     */
    public static int size() {
        return CONFIG_CACHE.size();
    }

    /**
     * 当前缓存的快照（可变副本，供外部与数据库最新值做比较，不影响缓存本身）
     * <p>
     * 定时热更新靠它判断"库里有没有变化"，BossRespawnConfigDO 由 Lombok @Data 生成
     * equals/hashCode，两次读到的同一行内容相同即相等。
     */
    public static Map<String, BossRespawnConfigDO> snapshot() {
        return new HashMap<>(CONFIG_CACHE);
    }
}
