package org.gms.soloMapling.ArtificialPlayer.BotCommandsPack;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.gms.client.Character;
import org.gms.client.inventory.Inventory;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.constants.inventory.ItemConstants;
import org.gms.server.maps.MapItem;
import org.gms.server.maps.MapleMap;
import org.gms.soloMapling.ArtificialPlayer.BotLogic;
import org.gms.util.PacketCreator;

/**
 * 机器人的物品操作：往地上丢、把地上的捡起来。
 *
 * <p>为什么不直接用 {@code Character#pickupItem}：那条路径里有
 * {@code InventoryManipulator.checkSpace(client, ...)}，内部还会走
 * {@code client.getPlayer()}，而无头客户端（BotClient 被所有机器人共用）的 player 恒为 null，
 * 一点就 NPE。这里改成「服务端直接搬运」：把 MapItem 从地图上摘掉、物品对象塞进背包格子，
 * 再补一个「物品消失」的广播让周围的人看到。
 *
 * @author WorkBuddy
 * @since 2026-11-02
 */
@Slf4j
public final class DropCommands {

    private DropCommands() {
    }

    /**
     * 背包里某样东西的总数（四个道具栏都翻）。
     */
    public static int countItem(Character bot, int itemId) {
        if (bot == null) {
            return 0;
        }
        InventoryType type = ItemConstants.getInventoryType(itemId);
        Inventory inv = bot.getInventory(type);
        return inv == null ? 0 : inv.countById(itemId);
    }

    /**
     * 从背包里扣掉 {@code qty} 个（分散在多个格子也能扣完）。
     *
     * @return 实际扣掉的数量
     */
    public static int consumeItem(Character bot, int itemId, int qty) {
        if (bot == null || qty <= 0) {
            return 0;
        }
        Inventory inv = bot.getInventory(ItemConstants.getInventoryType(itemId));
        if (inv == null) {
            return 0;
        }
        int remaining = qty;
        int removed = 0;
        // findById 只给一个格子；循环复制一份列表再扣，避免边遍历边改
        while (remaining > 0) {
            Item item = inv.findById(itemId);
            if (item == null) {
                break;
            }
            int take = Math.min(remaining, item.getQuantity());
            if (take <= 0) {
                break;
            }
            inv.removeItem(item.getPosition(), (short) take, true);
            removed += take;
            remaining -= take;
        }
        return removed;
    }

    /**
     * 往脚下丢 {@code qty} 个。物品从背包扣，扣不到就什么都不做。
     *
     * @return 真的丢出去了吗
     */
    public static boolean throwItem(Character bot, int itemId, int qty) {
        if (bot == null || bot.getMap() == null || qty <= 0) {
            return false;
        }
        int removed = consumeItem(bot, itemId, qty);
        if (removed <= 0) {
            return false;
        }
        MapleMap map = bot.getMap();
        Item drop = new Item(itemId, (short) 0, (short) removed);
        // owner 传机器人自己：这样它自己可以捡回来，别人也不会抢（OPQ 里就是「交件」用的）
        map.spawnItemDrop(bot, bot, drop, bot.getPosition(), false, true);
        return true;
    }

    /**
     * 捡起附近指定 id 的地面物品。
     *
     * @param itemIds 只捡这些 id（空集合表示什么都捡）
     * @param rangePx 以机器人脚下为中心的水平范围
     * @return 捡起的件数
     */
    public static int lootItems(Character bot, List<Integer> itemIds, int rangePx) {
        if (bot == null || bot.getMap() == null) {
            return 0;
        }
        MapleMap map = bot.getMap();
        Point me = bot.getPosition();
        int looted = 0;
        for (MapObjectHolder holder : snapshot(bot, itemIds, rangePx)) {
            if (pickup(bot, map, holder.item, me)) {
                looted++;
            }
        }
        return looted;
    }

    /**
     * 捡起地图上所有指定 id 的物品（不管多远），OPQ 清理战场用。
     */
    public static int lootItems(Character bot, List<Integer> itemIds) {
        return lootItems(bot, itemIds, 2000);
    }

    /**
     * 拾取指定的地上物品列表（上游 {@code lootItemListOnFloor}）。
     *
     * <p>OPQ 里由 {@code BotLogic.checkForItemsOnFloor} 先按坐标+物品 id 过滤出候选，
     * 再把这些 {@code MapItem} 直接交给这里搬运，省掉「再按 id 全地图找一遍」的重复扫描。
     *
     * @return 实际搬进背包的件数
     */
    public static int lootItemListOnFloor(Character bot, List<org.gms.server.maps.MapObject> found) {
        if (bot == null || bot.getMap() == null || found == null || found.isEmpty()) {
            return 0;
        }
        MapleMap map = bot.getMap();
        Point me = bot.getPosition();
        int looted = 0;
        for (org.gms.server.maps.MapObject obj : found) {
            if (obj instanceof MapItem mapItem && pickup(bot, map, mapItem, me)) {
                looted++;
            }
        }
        return looted;
    }

    /**
     * 往地上丢「一叠」{@code itemId}（上游 {@code botThrowItemQty} 的单件版本）。
     *
     * <p><b>不扣背包</b>：上游的做法就是凭空造一个干净物品再生成到地上，机器人背包里
     * 有几个根本不影响丢出来的数量。这正是 OPQ「显示交 5 个、实际只掉 1 个」的修法。
     *
     * <p>{@code ffaDrop=true} 让掉落对所有人立即可捡（pickup 不受归属保护），
     * 否则真人玩家要等归属保护过期才能捡。
     */
    public static boolean botThrowItem(Character bot, int itemId, Point throwPos) {
        return botThrowItemQty(bot, itemId, 1, throwPos);
    }

    /**
     * 丢一叠 {@code qty} 个（上游 {@code botThrowItemQty}）。
     */
    public static boolean botThrowItemQty(Character bot, int itemId, int qty, Point throwPos) {
        if (bot == null || bot.getMap() == null || itemId <= 0 || qty <= 0) {
            return false;
        }
        Item drop = BotLogic.generateCleanItemWithQty(itemId, qty);
        bot.getMap().spawnItemDrop(bot, bot, drop, throwPos == null ? bot.getPosition() : throwPos,
                true, false);
        return true;
    }

    // ------------------------------------------------------------------ 内部

    private record MapObjectHolder(MapItem item) {
    }

    private static List<MapObjectHolder> snapshot(Character bot, List<Integer> itemIds, int rangePx) {
        List<MapObjectHolder> found = new ArrayList<>();
        MapleMap map = bot.getMap();
        Point me = bot.getPosition();
        for (var obj : map.getItems()) {
            if (!(obj instanceof MapItem mapItem)) {
                continue;
            }
            if (mapItem.isPickedUp()) {
                continue;
            }
            int itemId = mapItem.getItemId();
            if (itemIds != null && !itemIds.isEmpty() && !itemIds.contains(itemId)) {
                continue;
            }
            if (me != null && Math.abs(mapItem.getPosition().x - me.x) > rangePx) {
                continue;
            }
            found.add(new MapObjectHolder(mapItem));
            if (found.size() >= 30) {
                break; // 一轮别捡太多，剩下的下一帧继续
            }
        }
        return found;
    }

    private static boolean pickup(Character bot, MapleMap map, MapItem mapItem, Point me) {
        mapItem.lockItem();
        try {
            if (mapItem.isPickedUp()) {
                return false;
            }
            mapItem.setPickedUp(true);
        } finally {
            mapItem.unlockItem();
        }

        Item item = mapItem.getItem();
        InventoryType type = ItemConstants.getInventoryType(item.getItemId());
        Inventory inv = bot.getInventory(type);
        if (inv == null || inv.addItem(item) == -1) {
            // 背包满了：把状态退回去，东西留在地上
            mapItem.lockItem();
            try {
                mapItem.setPickedUp(false);
            } finally {
                mapItem.unlockItem();
            }
            return false;
        }

        map.removeMapObject(mapItem);
        map.broadcastMessage(PacketCreator.removeItemFromMap(mapItem.getObjectId(), 2, bot.getId(), false, 0));
        return true;
    }
}
