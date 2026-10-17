package org.gms.soloMapling.ArtificialPlayer;

import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.constants.inventory.ItemConstants;
import org.gms.server.ItemInformationProvider;

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
}
