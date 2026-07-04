var status;

// ===================== 配置区 =====================
// 戒指阶段对应的物品ID，请根据实际配置替换
var RING_IDS = [
    1112000,  // 阶段1 戒指ID
    1112000,  // 阶段2 戒指ID
    1112000,  // 阶段3 戒指ID
    1112000,  // 阶段4 戒指ID
    1112000,  // 阶段5 戒指ID
    1112000,  // 阶段6 戒指ID
    1112000,  // 阶段7 戒指ID
    1112000,  // 阶段8 戒指ID
    1112000,  // 阶段9 戒指ID
    1112000   // 阶段10 戒指ID
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

            // 获取各类数量
            var totalCards = monsterBook.getTotalCards();       // 总收集卡数
            var normalCards = monsterBook.getNormalCard();      // 普通卡数
            var specialCards = monsterBook.getSpecialCard();    // 特殊卡数
            var bookLevel = monsterBook.getBookLevel();         // 图鉴等级

            // 获取所有卡片 Map，并统计数量
            var cardsMap = monsterBook.getCards();
            var cardCount = cardsMap.size();                    // 不同种类卡数

            var count = 0;
            for (const value of cardsMap.values()) {
                if(value == 5){
                    count++;
                }
            }

            var text = "#b===== 怪物卡收集兑换戒指 =====#k\r\n\r\n";
            text += "你当前怪怪卡收集进度:\r\n";
            text += "图鉴等级: #r" + bookLevel + "#k\r\n";
            text += "总收集卡数: #r" + totalCards + "#k\r\n";
            text += "普通卡数量: #r" + normalCards + "#k\r\n";
            text += "特殊卡数量: #r" + specialCards + "#k\r\n";
            text += "卡片种类数: #r" + cardCount + "#k\r\n";
            text += "收集完成卡片数量: #r" + count + "#k\r\n";
            text += "请选择要兑换的戒指阶段:\r\n";

            for (var i = 0; i < 10; i++) {
                var stage = i + 1;
                var canClaim = count >= REQUIRED_CARDS[i];
                var gemIcon = "#v" + GEM_IDS[i] + "#";
                var ringIcon = "#v" + RING_IDS[i] + "#";
                var statusStr = canClaim ? "#g[可兑换]#k" : "#r[未达标]#k";
                text += "#L" + i + "#" + ringIcon + " 阶段" + stage + " " + statusStr + " (需" + REQUIRED_CARDS[i] + "张卡 + " + gemIcon + "×" + REQUIRED_GEMS + ")#l\r\n";
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
                cm.sendOk("你的怪物卡收集数量不足。\r\n当前: #r" + count + "#k 张\r\n需要: #r" + requiredCard + "#k 张");
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

            // 扣除宝石
            cm.gainItem(gemId, -REQUIRED_GEMS);

            // 给予戒指
            cm.gainItem(ringId, 1);

            cm.sendOk("恭喜！你成功兑换了 #b阶段" + stage + "#k 的戒指！\r\n\r\n" +
                "消耗: #v" + gemId + "# " + gemName + " ×" + REQUIRED_GEMS + "\r\n" +
                "获得: #v" + ringId + "# 阶段" + stage + "戒指");
            cm.dispose();
        }
    }
}