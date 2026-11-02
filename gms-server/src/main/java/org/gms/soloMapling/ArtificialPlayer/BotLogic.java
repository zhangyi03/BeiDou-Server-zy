package org.gms.soloMapling.ArtificialPlayer;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.gms.client.Character;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.constants.inventory.ItemConstants;
import org.gms.server.ItemInformationProvider;
import org.gms.server.maps.MapItem;
import org.gms.server.maps.MapObject;
import org.gms.server.maps.MapObjectType;

/**
 * 机器人通用的「造物品」工具。
 *
 * <p>上游 BotLogic 是个 355 行的杂物间（还带着掉落、NPC、走位常量等依赖），
 * 本轮只移植生成链路真正用到的干净物品构造，其余留待移动/交易系统移植时再补。
 */
public class BotLogic {

    public static Item generateCleanItemEquip(int id) {
        Item cleanItem;
        if (ItemConstants.getInventoryType(id) == InventoryType.EQUIP) {
            cleanItem = generateCleanEquip(id);
        } else {
            cleanItem = generateCleanItem(id);
        }
        return cleanItem;
    }

    public static Item generateCleanEquip(int itemId) {
        return ItemInformationProvider.getInstance().getEquipById(itemId);
    }

    public static Item generateCleanItem(int itemId) {
        return generateCleanItemWithQty(itemId, 1);
    }

    public static Item generateCleanItemWithQty(int itemId, int qty) {
        return new Item(itemId, (short) 0, (short) qty);
    }

    // ------------------------------------------------------------------ 坐标

    public static boolean isPointNear(Point p1, Point p2) {
        return isPointNear(p1, p2, 25);
    }

    public static boolean isPointNear(Point p1, Point p2, double maxDistance) {
        if (p1 == null || p2 == null) {
            return false;
        }
        return p1.distance(p2) <= maxDistance;
    }

    /**
     * 同一平台判定：y 差在 {@code yThreshold} 内，且 x 差不超过 {@code maxXDistance}。
     */
    public static boolean isPointNearSameY(Point p1, Point p2, double maxXDistance, int yThreshold) {
        if (p1 == null || p2 == null) {
            return false;
        }
        if (Math.abs(p1.y - p2.y) > yThreshold) {
            return false;
        }
        return Math.abs(p1.x - p2.x) <= maxXDistance;
    }

    // -------------------------------------------------------------- 地面物品

    /**
     * 读某个位置附近地面上的掉落物（不区分 id）。
     *
     * <p><b>range 是「半径（px）」，不是平方。</b>{@code MapleMap.getMapObjectsInRange}
     * 的第二个参数要的是 {@code distanceSq}，由本方法负责平方。
     *
     * <p><b>调用方一定要给小的半径。</b>云片的落点由 {@code MapleMap.calcDropPos} 吸附到
     * 云朵正下方的地面，机器人正好站在那一层，横向几十像素就是自己那一片；半径给到上千
     * 就会把隔壁机器人打碎的、隔着几个平台的云片一起吸过来（抢战果 + 交件数量虚高）。
     * OPQ 现在用 {@code STAGE_1_LOOT_SCAN_RANGE_PX = 150}。
     */
    public static List<MapObject> readItemOnFloor(Character chr, Point location, double range) {
        if (chr == null || chr.getMap() == null || location == null) {
            return new ArrayList<>();
        }
        return chr.getMap().getMapObjectsInRange(location, range * range, Arrays.asList(MapObjectType.ITEM));
    }

    /**
     * 只保留指定 id 的掉落物。
     */
    public static List<MapObject> filterDroppedItems(List<MapObject> items, int[] filterList) {
        List<Integer> allowed = Arrays.stream(filterList).boxed().collect(Collectors.toList());
        Iterator<MapObject> iterator = items.iterator();
        while (iterator.hasNext()) {
            MapObject obj = iterator.next();
            if (!(obj instanceof MapItem mapItem) || !allowed.contains(mapItem.getItemId())) {
                iterator.remove();
            }
        }
        return items;
    }

    public static List<MapObject> checkForItemsOnFloor(Character chr, Point location, int[] itemIds) {
        return checkForItemsOnFloor(chr, location, 5000, itemIds);
    }

    public static List<MapObject> checkForItemsOnFloor(Character chr, Point location, double range, int[] itemIds) {
        List<MapObject> itemsOnFloor = readItemOnFloor(chr, location, range);
        return filterDroppedItems(itemsOnFloor, itemIds);
    }
}
