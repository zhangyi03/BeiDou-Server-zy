package org.gms.soloMapling.ArtificialPlayer.BotDecoratorSystem;

import org.gms.constants.inventory.EquipType;
import org.gms.dao.entity.BotEquipNxPoolDO;
import org.gms.soloMapling.config.BotConfigBridge;
import org.gms.soloMapling.itemPool.EquipMetadataCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * NX（点装）随机池，数据源为 {@code bot_equip_nx_pool} 表。
 *
 * <p>上游从 {@code NXItemPool.yaml} 读取，本版改成纯数据库单路径：库空只打错误日志，
 * 不再回退文件。
 *
 * <p>{@link #load()} 里「分类为空时从 {@link EquipMetadataCache} 自动补点装」的 fallback
 * 是<b>真实业务逻辑</b>（部分分类历来没人工挑过，靠 WZ 全量兜底），与数据来源无关，必须保留。
 *
 * @author WorkBuddy
 * @since 2026-10-17
 */
public class NXItemPool {

    private static final Logger log = LoggerFactory.getLogger(NXItemPool.class);

    public static final int GENDER_MALE = 0;
    public static final int GENDER_FEMALE = 1;
    public static final int GENDER_UNISEX = 2;

    // Categories whose gender follows the v83 body-equip ID-digit convention.
    // Non-body categories (weapons/earrings/face/eye/rings) don't encode gender
    // in the ID the same way, so they default to unisex unless the DB row says
    // otherwise.
    private static final Set<String> BODY_SLOT_CATEGORIES = Set.of(
            "caps", "tops", "bottoms", "overalls", "shoes", "gloves", "capes"
    );

    public static class PoolItem {
        final int id;
        final int gender; // 0 male, 1 female, 2 unisex

        PoolItem(int id, int gender) {
            this.id = id;
            this.gender = gender;
        }
    }

    /**
     * Maps NXItemPool category names to EquipType(s) for auto-population
     * from the {@link EquipMetadataCache} when the DB list is empty.
     * Weapons span many EquipTypes so they get their own array.
     */
    private static final Map<String, EquipType[]> CATEGORY_TO_EQUIP_TYPES = Map.ofEntries(
            Map.entry("caps", new EquipType[]{EquipType.CAP}),
            Map.entry("tops", new EquipType[]{EquipType.COAT}),
            Map.entry("bottoms", new EquipType[]{EquipType.PANTS}),
            Map.entry("overalls", new EquipType[]{EquipType.LONGCOAT}),
            Map.entry("shoes", new EquipType[]{EquipType.SHOES}),
            Map.entry("gloves", new EquipType[]{EquipType.GLOVES}),
            Map.entry("capes", new EquipType[]{EquipType.CAPE}),
            Map.entry("earrings", new EquipType[]{EquipType.EARRING}),
            Map.entry("face", new EquipType[]{EquipType.FACE}),
            Map.entry("eye", new EquipType[]{EquipType.ACCESSORY}),
            Map.entry("rings", new EquipType[]{EquipType.RING}),
            Map.entry("weapons", new EquipType[]{
                    EquipType.SWORD, EquipType.AXE, EquipType.MACE, EquipType.DAGGER,
                    EquipType.WAND, EquipType.STAFF, EquipType.SWORD_2H, EquipType.AXE_2H,
                    EquipType.MACE_2H, EquipType.SPEAR, EquipType.POLEARM, EquipType.BOW,
                    EquipType.CROSSBOW, EquipType.CLAW, EquipType.KNUCKLER, EquipType.PISTOL
            })
    );

    private static final Map<String, List<PoolItem>> pools = new HashMap<>();
    private static volatile boolean loaded = false;

    public static synchronized void load() {
        if (loaded) return;

        List<BotEquipNxPoolDO> rows = BotConfigBridge.nxPool();
        if (rows == null || rows.isEmpty()) {
            log.error("bot_equip_nx_pool 未读到任何启用行：NX 点装只会来自 EquipMetadataCache 兜底，"
                    + "请检查种子数据或表名");
            rows = List.of();
        }

        int itemCount = 0;
        for (BotEquipNxPoolDO row : rows) {
            if (row == null || row.getCategory() == null || row.getItemId() == null) continue;
            String category = row.getCategory();
            PoolItem item = parseRow(row);
            if (item == null) continue;
            pools.computeIfAbsent(category, k -> new ArrayList<>()).add(item);
            itemCount++;
        }

        // Auto-populate empty categories from EquipMetadataCache
        int cacheCount = 0;
        if (EquipMetadataCache.isInitialized()) {
            EquipMetadataCache cache = EquipMetadataCache.get();
            for (Map.Entry<String, EquipType[]> mapping : CATEGORY_TO_EQUIP_TYPES.entrySet()) {
                String category = mapping.getKey();
                List<PoolItem> existing = pools.get(category);
                if (existing != null && !existing.isEmpty()) continue;

                List<PoolItem> cacheItems = loadCashItemsFromCache(cache, category, mapping.getValue());
                if (!cacheItems.isEmpty()) {
                    pools.put(category, cacheItems);
                    cacheCount += cacheItems.size();
                    log.info("[NXItemPool] 分类 '{}' 库里为空，用 EquipMetadataCache 补了 {} 件点装",
                            category, cacheItems.size());
                }
            }
        } else {
            log.warn("[NXItemPool] EquipMetadataCache 未初始化，库里为空的分类将拿不到点装");
        }

        loaded = true;
        log.info("[NXItemPool] 从数据库加载 {} 条点装 + 缓存兜底 {} 条，覆盖 {} 个分类",
                itemCount, cacheCount, pools.size());
    }

    /**
     * Pull all cash equips for the given EquipTypes from the pre-built cache.
     * Pure in-memory — no WZ access.
     */
    private static List<PoolItem> loadCashItemsFromCache(EquipMetadataCache cache,
                                                         String category, EquipType[] equipTypes) {
        List<PoolItem> result = new ArrayList<>();
        for (EquipType eqType : equipTypes) {
            for (EquipMetadataCache.EquipEntry entry : cache.getCashByType(eqType)) {
                result.add(new PoolItem(entry.id, deriveGender(entry.id, category, null)));
            }
        }
        return result;
    }

    private static PoolItem parseRow(BotEquipNxPoolDO row) {
        int id = row.getItemId();
        Integer override = parseGender(row.getGender());
        return new PoolItem(id, deriveGender(id, row.getCategory(), override));
    }

    private static int deriveGender(int itemId, String category, Integer override) {
        if (override != null) return override;
        if (BODY_SLOT_CATEGORIES.contains(category)) {
            return (itemId / 1000) % 10;
        }
        return GENDER_UNISEX;
    }

    private static Integer parseGender(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase();
        switch (s) {
            case "male":
            case "0":
                return GENDER_MALE;
            case "female":
            case "1":
                return GENDER_FEMALE;
            case "unisex":
            case "2":
                return GENDER_UNISEX;
            default:
                return null;
        }
    }

    /**
     * Pick a random NX item from the given category for a bot of the given
     * gender. Filters by gender (unisex items always pass). Returns null if the
     * category is empty, the pool isn't loaded, or no item is eligible.
     */
    public static Integer getRandom(String category, int botGender) {
        if (!loaded) return null;
        List<PoolItem> list = pools.get(category);
        if (list == null || list.isEmpty()) return null;

        List<PoolItem> eligible = new ArrayList<>(list.size());
        for (PoolItem item : list) {
            if (item.gender == GENDER_UNISEX || item.gender == botGender) {
                eligible.add(item);
            }
        }
        if (eligible.isEmpty()) return null;

        return eligible.get(ThreadLocalRandom.current().nextInt(eligible.size())).id;
    }

    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * Force a reload — clears existing pools and re-reads DB + cache.
     * 供管理端「保存后立即生效」使用，不用重启服务。
     */
    public static synchronized void forceReload() {
        pools.clear();
        loaded = false;
        load();
    }
}
