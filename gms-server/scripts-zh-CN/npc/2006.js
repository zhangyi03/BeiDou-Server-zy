var status;

var InventoryType = Java.type('org.gms.client.inventory.InventoryType');


// ===================== 配置区 =====================
// 戒指阶段对应的物品ID，请根据实际配置替换
var RING_IDS = [
    1113000,  // 阶段1 戒指ID
    1113001,  // 阶段2 戒指ID
    1113002,  // 阶段3 戒指ID
    1113003,  // 阶段4 戒指ID
    1113004,  // 阶段5 戒指ID
    1113005,  // 阶段6 戒指ID
    1113006,  // 阶段7 戒指ID
    1113007,  // 阶段8 戒指ID
    1113008,  // 阶段9 戒指ID
    1113009   // 阶段10 戒指ID
];

// 所需宝石ID
var GEM_IDS = [
    4021000,  // 石榴石
    4021001,  // 紫水晶
    4021002,  // 海蓝宝石
    4021003,  // 祖母绿
    4021004,  // 蛋白石
    4021005,  // 蓝宝石
    4021006,  // 黄晶
    4021007,  // 钻石
    4021008,  // 黑水晶
    4021009   // 星石
];

// 宝石名称
var GEM_NAMES = [
    "石榴石", "紫水晶", "海蓝宝石", "祖母绿", "蛋白石",
    "蓝宝石", "黄晶", "钻石", "黑水晶", "星石"
];

// 所需卡组数量
var REQUIRED_CARDS = [30, 60, 90, 120, 150, 180, 210, 240, 270, 300];

// 所需宝石数量
var REQUIRED_GEMS = 10;
// =================================================

function start() {
    status = -1;
    action(1, 0, 0);
}

function action(mode, type, selection) {
    if (mode == -1) {
        cm.dispose();
    } else {
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
            var monsterBook = cm.getPlayer().getMonsterBook();

            // 获取所有卡片 Map，并统计数量
            var cardsMap = monsterBook.getCards();
            var count = 0;
            for (const value of cardsMap.values()) {
                if(value == 5){
                    count++;
                }
            }

            var text = "你当前已收集完成的怪物卡种类为: #b"+count+"#k\r\n";
            text += "请选择要兑换的戒指阶段:\r\n";

            for (var i = 0; i < 10; i++) {
                var stage = i + 1;
                var canClaim = count >= REQUIRED_CARDS[i];
                var gemIcon = "#v" + GEM_IDS[i] + "#";
                var ringIcon = "#v" + RING_IDS[i] + "#";
                var statusStr = canClaim ? "#g[可兑换]#k" : "#r[未达标]#k";
                text += "#L" + i + "#" + ringIcon + " 阶段" + stage + " " + statusStr + " (需" + REQUIRED_CARDS[i] + "种卡 + " + gemIcon + "×" + REQUIRED_GEMS + ")#l\r\n";
            }

            cm.sendSimple(text);
        } else if (status == 1) {
            if (selection < 0 || selection > 9) {
                cm.sendOk("选择无效。");
                cm.dispose();
                return;
            }

            var stage = selection + 1;
            var monsterBook = cm.getPlayer().getMonsterBook();
            var cardsMap = monsterBook.getCards();
            var count = 0;
            for (const value of cardsMap.values()) {
                if(value == 5){
                    count++;
                }
            }
            var ringId = RING_IDS[selection];
            var gemId = GEM_IDS[selection];
            var gemName = GEM_NAMES[selection];
            var requiredCard = REQUIRED_CARDS[selection];

            // 检查卡组数量
            if (count < requiredCard) {
                cm.sendOk("你的怪物卡收集数量不足。\r\n当前: #r" + count + "#k 种\r\n需要: #r" + requiredCard + "#k 种");
                cm.dispose();
                return;
            }

            // 检查宝石数量
            var gemCount = cm.getItemQuantity(gemId);
            if (gemCount < REQUIRED_GEMS) {
                cm.sendOk("#v" + gemId + "# " + gemName + "数量不足。\r\n当前: #r" + gemCount + "#k 个\r\n需要: #r" + REQUIRED_GEMS + "#k 个");
                cm.dispose();
                return;
            }

            // 检查背包空间
            if (!cm.canHold(ringId, 1)) {
                cm.sendOk("你的背包空间不足，请清理后再来。");
                cm.dispose();
                return;
            }

            // 除第一次领取外需检查戒指
            if(selection > 0){
                var ringOldId = RING_IDS[selection-1];
                if(cm.haveItem(ringOldId)){
                    //扣除旧戒指
                    cm.gainItem(ringOldId, -1);
                }else{
                    cm.sendOk("你需要换取新戒指吗？把戒指放在背包里让我看到吧。");
                    cm.dispose();
                    return;
                }
            }else{
                // 第一次需检查有没有有没有怪物戒指
                var flag = false;
                // 获取身上装备栏（已穿戴）
                var equippedInv = cm.getPlayer().getInventory(InventoryType.EQUIPPED);
                var items = equippedInv.list();
                var iter = items.iterator();
                while (iter.hasNext()) {
                    var item = iter.next();
                    var itemId = item.getItemId();      // 物品ID
                    if (itemId>=1113000 && itemId<=1113009){
                        flag = true;
                    }
                }
                // 获取背包装备栏（未穿戴）
                var equipInv = cm.getPlayer().getInventory(InventoryType.EQUIP);
                var items2 = equipInv.list();
                var iter2 = items2.iterator();
                while (iter2.hasNext()) {
                    var item2 = iter2.next();
                    var itemId2 = item2.getItemId();      // 物品ID
                    if (itemId2>=1113000 && itemId2<=1113009){
                        flag = true;
                    }
                }
                if (flag){
                    cm.sendOk("你已经有戒指了。");
                    cm.dispose();
                    return;
                }
            }

            // 扣除宝石
            cm.gainItem(gemId, -REQUIRED_GEMS);

            // 给予新戒指
            cm.gainItem(ringId, 1);

            cm.sendOk("恭喜！你成功兑换了 #b阶段" + stage + "#k 的戒指！\r\n\r\n" +
                "消耗: #v" + gemId + "# " + gemName + " ×" + REQUIRED_GEMS + "\r\n" +
                "获得: #v" + ringId + "# 阶段" + stage + "戒指");
            cm.dispose();
        }
    }
}