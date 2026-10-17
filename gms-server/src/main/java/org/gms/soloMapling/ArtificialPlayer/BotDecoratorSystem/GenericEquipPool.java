package org.gms.soloMapling.ArtificialPlayer.BotDecoratorSystem;

import org.gms.dao.entity.BotEquipGenericPoolDO;
import org.gms.server.ItemInformationProvider;
import org.gms.soloMapling.config.BotConfigBridge;
import org.gms.soloMapling.itemPool.EquipOmitList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 通用/无职业限制的装备随机池，数据源为 {@code bot_equip_generic_pool} 表。
 *
 * <p>上游从 {@code GenericEquipPool.yaml} 读取，本版改成纯数据库单路径：库空只打错误日志，
 * 不再回退文件。
 *
 * <p>每个物品的 {@code reqLevel} 仍在加载时从 WZ 缓存一次（{@link ItemInformationProvider#getEquipLevelReq}），
 * 之后挑选是纯内存计算：硬性过滤「等级不够的装备」，再按「离 bot 等级越近权重越高」加权随机，
 * 给低于等级的「时装款」留 5% 的尾巴。
 *
 * @author WorkBuddy
 * @since 2026-10-17
 */
public class GenericEquipPool {

    private static final Logger log = LoggerFactory.getLogger(GenericEquipPool.class);

    // Higher = stricter preference for gear near the bot's level. 0.05 gives a gentle tail
    // so a level 95 bot can still occasionally roll a level-35 whip for fashion.
    private static final double LEVEL_DECAY = 0.05;
    // Every eligible item keeps at least this relative weight, so outliers still occur.
    private static final double FASHION_FLOOR = 0.05;

    // Gender constants matching Character.getGender(): 0 = male, 1 = female, 2 = unisex.
    public static final int GENDER_MALE = 0;
    public static final int GENDER_FEMALE = 1;
    public static final int GENDER_UNISEX = 2;

    public static class PoolItem {
        final int id;
        final int minLevel;
        final int gender; // 0 male, 1 female, 2 unisex

        PoolItem(int id, int minLevel, int gender) {
            this.id = id;
            this.minLevel = minLevel;
            this.gender = gender;
        }
    }

    /**
     * Derive gender from the classic v83 equip-ID convention: the 4th digit
     * (thousands place) is 0 = male, 1 = female, 2 = unisex. This matches how
     * the client itself classifies equips — WZ has no separate reqGender field
     * for standard body equips.
     */
    private static int genderFromItemId(int itemId) {
        return (itemId / 1000) % 10;
    }

    private static final Map<String, List<PoolItem>> pools = new HashMap<>();
    private static volatile boolean loaded = false;

    /**
     * 从数据库加载通用装备池，并缓存每件装备的 WZ 需求等级。
     * 可重复调用——只加载一次；要热更新用 {@link #forceReload()}。
     */
    public static synchronized void load() {
        if (loaded) return;

        List<BotEquipGenericPoolDO> rows = BotConfigBridge.genericPool();
        if (rows == null || rows.isEmpty()) {
            log.error("bot_equip_generic_pool 未读到任何启用行：QuickEquip 会退化成「什么也不穿」，"
                    + "请检查种子数据或表名");
            rows = List.of();
        }

        ItemInformationProvider iip = ItemInformationProvider.getInstance();
        int itemCount = 0;

        for (BotEquipGenericPoolDO row : rows) {
            if (row == null || row.getCategory() == null || row.getItemId() == null) continue;
            int id = row.getItemId();
            int min = iip.getEquipLevelReq(id);
            int gender = genderFromItemId(id);
            pools.computeIfAbsent(row.getCategory(), k -> new ArrayList<>())
                    .add(new PoolItem(id, min, gender));
            itemCount++;
        }

        loaded = true;
        log.info("[GenericEquipPool] 从数据库加载 {} 件通用装备，覆盖 {} 个分类（reqLevel 来自 WZ）",
                itemCount, pools.size());
    }

    /**
     * Pick a random item ID from the given category for a bot at the given level
     * and gender. Never returns an item whose reqLevel exceeds botLevel or whose
     * gender doesn't match (unisex items are always eligible). Returns null if
     * the category is empty, the pool isn't loaded, or no item is eligible.
     *
     * @param botGender 0 = male, 1 = female (unisex items always pass).
     */
    public static Integer getRandom(String category, int botLevel, int botGender) {
        if (!loaded) return null;
        List<PoolItem> list = pools.get(category);
        if (list == null || list.isEmpty()) return null;

        EquipOmitList.load();

        // Single pass: filter by hard level cap + gender and build the weight table.
        List<PoolItem> eligible = new ArrayList<>(list.size());
        double[] weights = new double[list.size()];
        double total = 0.0;
        int n = 0;
        for (PoolItem item : list) {
            if (item.minLevel > botLevel) continue; // hard rule: never over-level
            if (item.gender != GENDER_UNISEX && item.gender != botGender) continue; // gender gate
            if (EquipOmitList.isOmitted(item.id)) continue; // central omit list (flag/junk items)
            double gap = botLevel - item.minLevel;
            double w = 1.0 / (1.0 + gap * LEVEL_DECAY);
            if (w < FASHION_FLOOR) w = FASHION_FLOOR;
            eligible.add(item);
            weights[n++] = w;
            total += w;
        }
        if (eligible.isEmpty()) return null;

        double roll = ThreadLocalRandom.current().nextDouble() * total;
        double acc = 0.0;
        for (int i = 0; i < n; i++) {
            acc += weights[i];
            if (roll <= acc) return eligible.get(i).id;
        }
        return eligible.get(n - 1).id;
    }

    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * 强制重载（管理端保存后调用，无需重启）。
     */
    public static synchronized void forceReload() {
        pools.clear();
        loaded = false;
        load();
    }
}
