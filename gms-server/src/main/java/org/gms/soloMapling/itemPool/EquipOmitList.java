package org.gms.soloMapling.itemPool;

import org.gms.dao.entity.BotEquipOmitListDO;
import org.gms.soloMapling.config.BotConfigBridge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 机器人出生时禁止自动穿戴的装备黑名单，数据源为 {@code bot_equip_omit_list} 表。
 *
 * <p>这些装备在 WZ 里完全合法（reqLevel / reqJob / gender 都对），只是穿在机器人身上
 * 很难看 —— 典型代表是「国旗」系列：装饰性旗杆被当武器挥，机器人「攻击」时像在挥旗子。
 *
 * <p>两条出生穿装路径（完整装饰的 getRandomEquip、QuickEquip 的 GenericEquipPool）都会查这里，
 * 命中就跳过该 id、重新摇同槽位的另一件。不影响 NX 点装层。
 *
 * <p>上游从 {@code EquipOmitList.yaml} 读取。本版改成纯数据库单路径。读不到数据时
 * <b>失败开放</b>（当空表处理），宁可让机器人穿得难看，也不能让它光着出生。
 *
 * @author WorkBuddy
 * @since 2026-10-17
 */
public class EquipOmitList {

    private static final Logger log = LoggerFactory.getLogger(EquipOmitList.class);

    private static volatile boolean enabled = true;
    private static volatile boolean loaded = false;
    private static final Set<Integer> omittedIds = new HashSet<>();
    private static final List<int[]> omittedRanges = new ArrayList<>();

    /**
     * 从数据库加载排除表。幂等 —— 只加载一次；要热更新用 {@link #forceReload()}。
     */
    public static synchronized void load() {
        if (loaded) return;

        try {
            List<BotEquipOmitListDO> rows = BotConfigBridge.omitList();
            if (rows == null || rows.isEmpty()) {
                log.error("bot_equip_omit_list 未读到任何启用行：机器人可能戴上国旗当武器，"
                        + "请检查种子数据或表名");
                rows = List.of();
            }

            for (BotEquipOmitListDO row : rows) {
                if (row == null) continue;
                if ("RANGE".equalsIgnoreCase(row.getBlockType())) {
                    int min = row.getRangeStart() == null ? 0 : row.getRangeStart();
                    int max = row.getRangeEnd() == null ? 0 : row.getRangeEnd();
                    if (min > max) {
                        int t = min;
                        min = max;
                        max = t;
                    }
                    if (min != max || min != 0) {
                        omittedRanges.add(new int[]{min, max});
                    }
                } else if (row.getItemId() != null && row.getItemId() > 0) {
                    omittedIds.add(row.getItemId());
                }
            }

            loaded = true;
            log.info("[EquipOmitList] 从数据库加载 {} 条排除 id 和 {} 个区间（enabled={}）",
                    omittedIds.size(), omittedRanges.size(), enabled);
        } catch (Exception e) {
            // Fail open: keep the list empty so bot spawn still works.
            loaded = true;
            log.error("[EquipOmitList] 读取失败，排除表按空处理（机器人会穿到国旗类装备）", e);
        }
    }

    /**
     * True if this item id should never be auto-equipped on a bot. Lazily loads
     * the table on first use, mirroring GenericEquipPool.
     */
    public static boolean isOmitted(int itemId) {
        if (!loaded) load();
        if (!enabled) return false;
        if (omittedIds.contains(itemId)) return true;
        for (int[] r : omittedRanges) {
            if (itemId >= r[0] && itemId <= r[1]) return true;
        }
        return false;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Runtime toggle for the whole list. 只影响本进程，不写库。
     */
    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /**
     * 强制重载（管理端保存后调用，无需重启）。
     */
    public static synchronized void forceReload() {
        omittedIds.clear();
        omittedRanges.clear();
        loaded = false;
        load();
    }
}
