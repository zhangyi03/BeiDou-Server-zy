package org.gms.soloMapling.ArtificialPlayer.BotDialogueSystem;

import org.gms.dao.entity.BotDialoguePoolDO;
import org.gms.soloMapling.config.BotConfigBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 机器人对话池，数据源为 {@code bot_dialogue_pool} 表。
 *
 * <p>以前摆摊名称（{@code BotShop#SHOP_DESCRIPTION}）和喊话广告词（{@code BotShop#AD_LINES}）
 * 都是硬编码常量，改文案要重编译。现在分成两个池放在库里：
 * <ul>
 *   <li>{@link #POOL_SHOP_NAME}——开摊时抽一条当个人商店的摊位标题；</li>
 *   <li>{@link #POOL_SHOUT}——摊主每隔 12~20 秒原地喊一句。</li>
 * </ul>
 *
 * <p>纯数据库单路径：池空时返回 {@code null}，由调用方退回自己的兜底文案，不静默变成空字符串。
 * 加载在第一次用到时发生（懒加载），管理端保存/删除后触发 {@link #forceReload()} 热更新。
 *
 * @author WorkBuddy
 * @since 2026-10-28
 */
public class BotDialoguePool {

    private static final Logger log = LoggerFactory.getLogger(BotDialoguePool.class);

    /**
     * 摆摊名称池
     */
    public static final String POOL_SHOP_NAME = "shop_name";

    /**
     * 喊话内容池
     */
    public static final String POOL_SHOUT = "shout";

    /**
     * 池里的一行
     */
    private static class Line {
        final String content;
        final int weight;

        Line(BotDialoguePoolDO row) {
            this.content = row.getContent() == null ? "" : row.getContent().trim();
            this.weight = Math.max(1, row.getWeight() == null ? 100 : row.getWeight());
        }
    }

    /**
     * pool_type -> 该池全部候选行
     */
    private static volatile Map<String, List<Line>> byType = Collections.emptyMap();
    private static volatile boolean loaded = false;

    private BotDialoguePool() {
    }

    /**
     * 首次使用时懒加载；可重复调用。
     */
    public static void load() {
        if (loaded) {
            return;
        }
        forceReload();
    }

    /**
     * 清缓存并重新查库。管理端保存/删除后调用。
     */
    public static synchronized void forceReload() {
        Map<String, List<Line>> grouped = new LinkedHashMap<>();
        for (String type : new String[]{POOL_SHOP_NAME, POOL_SHOUT}) {
            List<BotDialoguePoolDO> rows = BotConfigBridge.dialoguePool(type);
            List<Line> lines = new ArrayList<>();
            if (rows != null) {
                for (BotDialoguePoolDO row : rows) {
                    if (row == null || row.getContent() == null || row.getContent().isBlank()) {
                        continue;
                    }
                    lines.add(new Line(row));
                }
            }
            if (lines.isEmpty()) {
                log.warn("bot_dialogue_pool 里 {} 没有任何启用行，调用方会退回内置兜底文案", type);
            }
            grouped.put(type, lines);
        }
        byType = grouped;
        loaded = true;
        log.info("[BotDialoguePool] 已加载对话池：摆摊名称 {} 条，喊话 {} 条",
                grouped.get(POOL_SHOP_NAME).size(), grouped.get(POOL_SHOUT).size());
    }

    /**
     * 抽一条摆摊名称；池空返回 {@code null}。
     */
    public static String randomShopName() {
        return randomOf(POOL_SHOP_NAME);
    }

    /**
     * 抽一条喊话内容；池空返回 {@code null}。
     */
    public static String randomShoutLine() {
        return randomOf(POOL_SHOUT);
    }

    /**
     * 某个池里有多少条（日志/监控用）。
     */
    public static int size(String poolType) {
        load();
        List<Line> lines = byType.get(poolType);
        return lines == null ? 0 : lines.size();
    }

    private static String randomOf(String poolType) {
        load();
        List<Line> lines = byType.get(poolType);
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        int total = 0;
        for (Line line : lines) {
            total += line.weight;
        }
        int roll = ThreadLocalRandom.current().nextInt(Math.max(1, total));
        int acc = 0;
        for (Line line : lines) {
            acc += line.weight;
            if (roll < acc) {
                return line.content;
            }
        }
        return lines.get(lines.size() - 1).content;
    }
}
