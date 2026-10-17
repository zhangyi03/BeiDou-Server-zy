package org.gms.soloMapling;

import lombok.extern.slf4j.Slf4j;
import org.gms.client.Character;
import org.gms.client.inventory.Inventory;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.client.inventory.ItemFactory;
import org.gms.util.DatabaseConnection;
import org.gms.util.Pair;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

/**
 * 机器人物品落库。
 *
 * <p>机器人按真实玩家持久化，身上的装备必须写进 {@code inventoryitems} /
 * {@code inventoryequipment}，和真人共用同一张表、同一套行格式（{@code type=1}、
 * {@code characterid=cid}、装备位为负数）。这样 {@code Character.fromCharactersDO}
 * 里那段 {@code inventoryService.getInventoryList(...)} 加载逻辑不需要任何改动，
 * 机器人下次上线读到的就是上次随机的结果。
 *
 * <p><b>为什么不用 {@code Character.saveCharToDB}</b>：它的第一步
 * {@code Server.updateCharacterEntry} 会走 {@code World.registerAccountCharacterView}，
 * 内部是 {@code accountChars.get(accountId).put(...)}；机器人账号从未登录过，
 * {@code accountChars} 里没有这个 key，直接 NPE。所以这里只做「物品」这一件事，
 * 需要同步的角色字段（hair / face / skincolor）由调用方单独更新。
 *
 * @author WorkBuddy
 * @since 2026-10-17
 */
@Slf4j
public final class BotInventorySaver {

    private BotInventorySaver() {
    }

    /**
     * 把机器人内存里的全部物品整体写回数据库。
     *
     * <p>{@link ItemFactory#saveItems} 的语义是「按 {@code type + characterid} 全删再全插」，
     * 所以不需要先清表，也不会留下上一次的脏行；装备会同时在
     * {@code inventoryequipment} 里生成对应的属性行。
     *
     * @throws IllegalStateException 写库失败时抛出，由调用方决定是否只记日志
     */
    public static void saveItems(Character bot) {
        if (bot == null || bot.getId() <= 0) {
            throw new IllegalArgumentException("机器人对象或角色 id 无效，无法落库");
        }
        List<Pair<Item, InventoryType>> items = collectItems(bot);
        try (Connection con = DatabaseConnection.getConnection()) {
            ItemFactory.INVENTORY.saveItems(items, bot.getId(), con);
        } catch (Exception e) {
            throw new IllegalStateException("机器人 " + bot.getId() + " 物品落库失败", e);
        }
        log.info("[BotInventorySaver] 机器人 {}（cid={}）物品已落库，共 {} 件",
                bot.getName(), bot.getId(), items.size());
    }

    /**
     * 清空内存里「身上穿着的」装备栏（{@link InventoryType#EQUIPPED}）。
     *
     * <p>只动这一栏：消耗栏 / 其它栏 / 现金栏的物品不属于外观装饰，重置装备时不能连带清掉。
     *
     * <p><b>不会广播 {@code equipChanged()}</b>：清空后紧接着就会重新穿一套，
     * 中间广播会让同地图玩家看到一帧「裸体」。由调用方在穿完后统一广播一次。
     *
     * @return 清掉的件数
     */
    public static int clearEquippedInMemory(Character bot) {
        if (bot == null) {
            return 0;
        }
        Inventory equipped = bot.getInventory(InventoryType.EQUIPPED);
        List<Item> snapshot = new ArrayList<>(equipped.list());
        for (Item item : snapshot) {
            equipped.removeSlot(item.getPosition());
        }
        return snapshot.size();
    }

    /**
     * 装备栏是否已经有东西（用于「库里有装备就别再随机覆盖」的兜底判断）。
     */
    public static boolean hasEquipped(Character bot) {
        return bot != null && !bot.getInventory(InventoryType.EQUIPPED).list().isEmpty();
    }

    private static List<Pair<Item, InventoryType>> collectItems(Character bot) {
        List<Pair<Item, InventoryType>> items = new ArrayList<>();
        for (InventoryType type : InventoryType.values()) {
            if (type == InventoryType.UNDEFINED || type == InventoryType.CANHOLD) {
                continue;
            }
            Inventory inv = bot.getInventory(type);
            if (inv == null) {
                continue;
            }
            for (Item item : inv.list()) {
                items.add(new Pair<>(item, type));
            }
        }
        return items;
    }
}
