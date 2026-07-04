var status;

// 使用 Java.type 引入 InventoryType，供全脚本使用
var InventoryType = Java.type('org.gms.client.inventory.InventoryType');

// ===================== 配置区 =====================
// 戒指阶段对应的物品ID
var RING_IDS = [
    1113000, 1113001, 1113002, 1113003, 1113004,
    1113005, 1113006, 1113007, 1113008, 1113009
];

// 所需宝石ID
var GEM_IDS = [
    4021000, 4021001, 4021002, 4021003, 4021004,
    4021005, 4021006, 4021007, 4021008, 4021009
];

// 宝石名称
var GEM_NAMES = [
    "石榴石", "紫水晶", "海蓝宝石", "祖母绿", "蛋白石",
    "蓝宝石", "黄晶", "钻石", "黑水晶", "星石"
];

// 所需完成的怪物卡种类数
var REQUIRED_CARDS = [30, 60, 90, 120, 150, 180, 210, 240, 270, 300];

// 每阶段所需宝石数量
var REQUIRED_GEMS = 10;

// 怪物戒指ID范围（用于检测是否已持有）
var RING_MIN_ID = 1113000;
var RING_MAX_ID = 1113009;
// =================================================

function start() {
    status = -1;
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode == -1) {
        cm.dispose();
        return;
    }
    if (mode == 0 && type > 0) {
        cm.dispose();
        return;
    }
    if (mode == 1) {
        status++;
    } else {
        status--;
    }

    if (status == 0) {
        // 阶段选择页
        var cardCount = getCompletedCardCount();

        var text = "你好！这里是怪物卡戒指兑换处。\r\n";
        text += "你当前已收集完成的怪物卡种类为: #b" + cardCount + "#k\r\n\r\n";
        text += "请选择要兑换的戒指阶段:\r\n";

        for (var i = 0; i < 10; i++) {
            var stage = i + 1;
            var canClaim = cardCount >= REQUIRED_CARDS[i];
            var color = canClaim ? "#g" : "#r";
            var statusStr = canClaim ? "[可兑换]" : "[未达标]";

            text += "\r\n#L" + i + "#";
            text += "#v" + RING_IDS[i] + "# 阶段" + stage + " " + color + statusStr + "#k";
            text += " (需" + REQUIRED_CARDS[i] + "种卡 + #v" + GEM_IDS[i] + "#×" + REQUIRED_GEMS + ")#l";
        }

        cm.sendSimple(text);

    } else if (status == 1) {
        if (selection < 0 || selection > 9) {
            cm.sendOk("选择无效，请重新对话。");
            cm.dispose();
            return;
        }

        var stage = selection + 1;
        var ringId = RING_IDS[selection];
        var gemId = GEM_IDS[selection];
        var gemName = GEM_NAMES[selection];
        var requiredCard = REQUIRED_CARDS[selection];

        var cardCount = getCompletedCardCount();
        var gemCount = cm.getItemQuantity(gemId);

        // 1. 检查卡组数量
        if (cardCount < requiredCard) {
            cm.sendOk("你的怪物卡收集数量不足。\r\n\r\n" +
                      "当前: #r" + cardCount + "#k 种\r\n" +
                      "需要: #r" + requiredCard + "#k 种");
            cm.dispose();
            return;
        }

        // 2. 检查宝石数量
        if (gemCount < REQUIRED_GEMS) {
            cm.sendOk("#v" + gemId + "# #b" + gemName + "#k 数量不足。\r\n\r\n" +
                      "当前: #r" + gemCount + "#k 个\r\n" +
                      "需要: #r" + REQUIRED_GEMS + "#k 个");
            cm.dispose();
            return;
        }

        // 3. 检查背包空间
        if (!cm.canHold(ringId, 1)) {
            cm.sendOk("你的背包空间不足，请清理后再来。");
            cm.dispose();
            return;
        }

        // 4. 阶段递进检查
        if (selection > 0) {
            // 阶段2~10：需持有上一阶段戒指才能兑换
            var prevRingId = RING_IDS[selection - 1];
            if (!cm.haveItem(prevRingId)) {
                cm.sendOk("兑换阶段 #b" + stage + "#k 戒指，需要先将阶段 #b" + (stage - 1) + "#k 的戒指放在背包里。\r\n\r\n" +
                          "#v" + prevRingId + "# 阶段" + (stage - 1) + "戒指");
                cm.dispose();
                return;
            }
        } else {
            // 阶段1：检查是否已持有任何怪物戒指（身上或背包）
            if (hasAnyMonsterRing()) {
                cm.sendOk("你已经持有怪物戒指了，无法重复领取阶段1戒指。\r\n" +
                          "如需更换，请先兑换到更高阶段。");
                cm.dispose();
                return;
            }
        }

        // 5. 执行兑换
        // 扣除上一阶段戒指（阶段2~10）
        if (selection > 0) {
            cm.gainItem(RING_IDS[selection - 1], -1);
        }

        // 扣除宝石
        cm.gainItem(gemId, -REQUIRED_GEMS);

        // 给予新戒指
        cm.gainItem(ringId, 1);

        cm.sendOk("恭喜！你成功兑换了 #b阶段" + stage + "#k 的戒指！\r\n\r\n" +
                  "消耗: #v" + gemId + "# #b" + gemName + "#k ×" + REQUIRED_GEMS + "\r\n" +
                  "获得: #v" + ringId + "# 阶段" + stage + "戒指");
        cm.dispose();
    }
}

/**
 * 获取当前已收集完成的怪物卡种类数量（星级为5的卡片）
 */
function getCompletedCardCount() {
    var monsterBook = cm.getPlayer().getMonsterBook();
    var cardsMap = monsterBook.getCards();
    var count = 0;
    for (var value of cardsMap.values()) {
        if (value == 5) {
            count++;
        }
    }
    return count;
}

/**
 * 检查玩家身上或背包是否已持有任何怪物戒指（1113000~1113009）
 */
function hasAnyMonsterRing() {
    var equippedInv = cm.getPlayer().getInventory(InventoryType.EQUIPPED);
    var equipInv = cm.getPlayer().getInventory(InventoryType.EQUIP);

    return hasRingInRange(equippedInv) || hasRingInRange(equipInv);
}

/**
 * 在指定Inventory中检查是否存在指定ID范围的戒指
 */
function hasRingInRange(inventory) {
    var items = inventory.list();
    var iter = items.iterator();
    while (iter.hasNext()) {
        var item = iter.next();
        var itemId = item.getItemId();
        if (itemId >= RING_MIN_ID && itemId <= RING_MAX_ID) {
            return true;
        }
    }
    return false;
}
