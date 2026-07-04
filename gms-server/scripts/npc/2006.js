var status;

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
            // 获取玩家的怪怪卡图鉴对象
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

            // 构建显示文本
            var text = "#b===== 怪怪卡信息 =====#k\r\n";
            text += "\r\n";
            text += "图鉴等级: #r" + bookLevel + "#k\r\n";
            text += "总收集卡数: #r" + totalCards + "#k\r\n";
            text += "普通卡数量: #r" + normalCards + "#k\r\n";
            text += "特殊卡数量: #r" + specialCards + "#k\r\n";
            text += "卡片种类数: #r" + cardCount + "#k\r\n";
            text += "收集完成卡片数量: #r" + count + "#k\r\n";
            text += "\r\n";

            cm.sendOk(text);
            cm.dispose();
        }
    }
}