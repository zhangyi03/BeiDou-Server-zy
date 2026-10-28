package org.gms.soloMapling.ArtificialPlayer.BotShopSystem;

import org.gms.client.inventory.InventoryType;
import org.gms.constants.inventory.ItemConstants;
import org.gms.dao.entity.BotSellItemPoolDO;
import org.gms.soloMapling.config.BotConfigBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 摆摊出售机器人的货架物品池，数据源为 {@code bot_sell_item_pool} 表。
 *
 * <p>上游从 {@code itemConfig/*.yaml} 读，本版改成纯数据库单路径：库空只打错误日志，
 * 不回退文件。加载在第一次开摊时发生（懒加载），保存/删除后由管理端触发
 * {@link #forceReload()}，之后的补货立即用新数据。
 *
 * <p>抽签规则：
 * <ul>
 *   <li><b>按分类轮流</b>——先把分类顺序打乱，再第 i 格抽第 {@code i % 分类数} 类，
 *       避免整摊货全是药水（或加了装备/矿石之后整摊全是装备）；</li>
 *   <li><b>类内加权</b>——同一分类里按 {@code weight} 加权随机，权重高的更容易出现；</li>
 *   <li><b>一次不重复</b>——同一批货里不会出现两个相同的物品 ID。</li>
 * </ul>
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
public class SellItemPool {

    private static final Logger log = LoggerFactory.getLogger(SellItemPool.class);

    /**
     * 池里的一行：摆摊时直接转成 {@code PlayerShopItem}。
     */
    public static class PoolItem {
        public final String category;
        public final int itemId;
        public final int price;
        public final int qtyMin;
        public final int qtyMax;
        public final int weight;
        public final String note;
        /**
         * 是否装备。装备上架必须构造成 {@code Equip}（带属性段），不能当普通 {@code Item} 发，
         * 而且一格只能放一件，所以生成物品时按这个标记分流。
         */
        public final boolean equip;

        PoolItem(BotSellItemPoolDO row) {
            this.category = row.getCategory();
            this.itemId = row.getItemId() == null ? 0 : row.getItemId();
            this.price = row.getPrice() == null ? 0 : row.getPrice();
            this.qtyMin = Math.max(1, row.getQtyMin() == null ? 1 : row.getQtyMin());
            this.qtyMax = Math.max(this.qtyMin, row.getQtyMax() == null ? this.qtyMin : row.getQtyMax());
            this.weight = Math.max(1, row.getWeight() == null ? 100 : row.getWeight());
            this.note = row.getNote() == null ? "" : row.getNote();
            this.equip = itemId > 0
                    && ItemConstants.getInventoryType(itemId) == InventoryType.EQUIP;
        }
    }

    /**
     * category -> 该分类的全部物品；用 LinkedHashMap 保证遍历顺序稳定（抽签前还会打乱）
     */
    private static volatile Map<String, List<PoolItem>> byCategory = Collections.emptyMap();
    private static volatile boolean loaded = false;

    /**
     * 首次开摊时懒加载；可重复调用。
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
        List<BotSellItemPoolDO> rows = BotConfigBridge.sellPool();
        if (rows == null || rows.isEmpty()) {
            log.error("bot_sell_item_pool 未读到任何启用行：摆摊出售机器人会开空摊，"
                    + "请检查种子数据（V1.11.12）或表名");
            rows = List.of();
        }

        Map<String, List<PoolItem>> grouped = new LinkedHashMap<>();
        for (BotSellItemPoolDO row : rows) {
            if (row == null || row.getItemId() == null || row.getItemId() <= 0) {
                continue;
            }
            String category = row.getCategory() == null || row.getCategory().isBlank()
                    ? "misc" : row.getCategory();
            grouped.computeIfAbsent(category, k -> new ArrayList<>()).add(new PoolItem(row));
        }

        byCategory = grouped;
        loaded = true;
        int total = grouped.values().stream().mapToInt(List::size).sum();
        log.info("[SellItemPool] 已加载 {} 条货架物品，{} 个分类：{}", total, grouped.size(), grouped.keySet());
    }

    /**
     * 池里一共有多少条（只用于日志/监控）。
     */
    public static int size() {
        return byCategory.values().stream().mapToInt(List::size).sum();
    }

    /**
     * 抽 {@code count} 件不重复的物品；池空时返回空列表。
     *
     * <p>调用方拿到的是不可变快照，改动返回的列表不影响缓存。
     */
    public static List<PoolItem> randomItems(int count) {
        load();
        Map<String, List<PoolItem>> source = byCategory;
        if (source.isEmpty() || count <= 0) {
            return Collections.emptyList();
        }

        // 深拷贝一份，抽走的从副本里删，保证同一批货不重复
        Map<String, List<PoolItem>> remaining = new HashMap<>();
        List<String> categories = new ArrayList<>(source.size());
        for (Map.Entry<String, List<PoolItem>> entry : source.entrySet()) {
            remaining.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            categories.add(entry.getKey());
        }
        Collections.shuffle(categories, ThreadLocalRandom.current());

        List<PoolItem> picked = new ArrayList<>(count);
        int cursor = 0;
        while (picked.size() < count) {
            // 找下一个还有货的分类（游标轮转，保证分类间均匀）
            List<PoolItem> bucket = null;
            for (int i = 0; i < categories.size() && bucket == null; i++) {
                String category = categories.get((cursor + i) % categories.size());
                List<PoolItem> candidate = remaining.get(category);
                if (candidate != null && !candidate.isEmpty()) {
                    cursor = (cursor + i + 1) % categories.size();
                    bucket = candidate;
                }
            }
            if (bucket == null) {
                break; // 池子被抽空了
            }
            picked.add(weightedPick(bucket));
        }
        return Collections.unmodifiableList(picked);
    }

    /**
     * 在一组物品里按 weight 加权抽一件，并把它从桶里移除。
     */
    private static PoolItem weightedPick(List<PoolItem> bucket) {
        int total = 0;
        for (PoolItem item : bucket) {
            total += item.weight;
        }
        int roll = ThreadLocalRandom.current().nextInt(Math.max(1, total));
        int acc = 0;
        for (int i = 0; i < bucket.size(); i++) {
            acc += bucket.get(i).weight;
            if (roll < acc) {
                return bucket.remove(i);
            }
        }
        return bucket.remove(bucket.size() - 1);
    }
}
