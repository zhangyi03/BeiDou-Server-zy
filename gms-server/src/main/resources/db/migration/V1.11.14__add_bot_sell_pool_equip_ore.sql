-- =============================================================================
-- 摆摊出售物品池：补「矿石」与「装备」两类商品
--
-- 原来 V1.11.12 只导出了卷轴 / 暗黑卷轴 / 飞镖 / 药水四类，摊位品类偏窄。
-- 本脚本只做数据补充，不改表结构：
--
--   category='ore'   矿石 / 精炼矿 / 宝石 / 魔法石一共 44 条
--                    - 价格与分组取自 SoloMapling 的 itemConfig/etc.yaml
--                      （CrystalOre / CrystalRefined / MineralOre / MineralRefined /
--                        JewelOre / JewelRefined / SkillRocks）
--                    - weight 按「越稀有越低」排：粗矿 200、精炼 20~80
--                    - qty_* 是每格数量：粗矿 1~5、精炼 1~3、晶体贵金属 1~2
--
--   category='equip' 白板装备 37 条（15 把武器覆盖 15 个武器子类 + 22 件防具）
--                    - 只收 reqLevel 45~80、WZ 售价 >= 5 万的常规装备，
--                      排除现金道具(cash=1)、bot_equip_omit_list 里的旗帜/活动物品
--                    - price = WZ 的 info/price × 2（NPC 收购价的 2 倍左右）
--                    - qty_min = qty_max = 1：装备一格只能放一件
--
-- ⚠️ 装备行必须由服务端构造成真正的 Equip 对象再上架（BotShop#stock 已按
--    InventoryType.EQUIP 分流用 ItemInformationProvider#getEquipById 生成），
--    直接 new Item(...) 发出去的封包缺装备属性段，客户端会解析错位。
--
-- 幂等：uk_bot_sell_pool(category, item_id) 已存在的行直接跳过（INSERT IGNORE）。
-- =============================================================================

-- 分类注释补上新增的两类，方便在数据库工具里直接看懂 category 的取值范围
ALTER TABLE `bot_sell_item_pool`
  MODIFY COLUMN `category` VARCHAR(32) NOT NULL COMMENT 'scroll/darkscroll/star/potion/ore/equip';

-- 矿石 44 条（价格取自 SoloMapling etc.yaml）
INSERT IGNORE INTO `bot_sell_item_pool`
  (`category`, `item_id`, `price`, `qty_min`, `qty_max`, `weight`, `enabled`, `note`)
VALUES
  ('ore', 4004000, 20000, 1, 3, 100, 1, 'Power Crystal Ore'),
  ('ore', 4004001, 20000, 1, 3, 100, 1, 'Wisdom Crystal Ore'),
  ('ore', 4004002, 20000, 1, 3, 100, 1, 'DEX Crystal Ore'),
  ('ore', 4004003, 20000, 1, 3, 100, 1, 'LUK Crystal Ore'),
  ('ore', 4004004, 40000, 1, 2, 50, 1, 'Dark Crystal Ore'),
  ('ore', 4005000, 250000, 1, 2, 20, 1, 'Power Crystal'),
  ('ore', 4005001, 250000, 1, 2, 20, 1, 'Wisdom Crystal'),
  ('ore', 4005002, 250000, 1, 2, 20, 1, 'DEX Crystal'),
  ('ore', 4005003, 250000, 1, 2, 20, 1, 'LUK Crystal'),
  ('ore', 4005004, 450000, 1, 1, 10, 1, 'Dark Crystal'),
  ('ore', 4010000, 2000, 1, 5, 200, 1, 'Bronze Ore'),
  ('ore', 4010001, 2000, 1, 5, 200, 1, 'Steel Ore'),
  ('ore', 4010002, 3000, 1, 5, 200, 1, 'Mithril Ore'),
  ('ore', 4010003, 3000, 1, 5, 200, 1, 'Adamantium Ore'),
  ('ore', 4010004, 3000, 1, 5, 200, 1, 'Silver Ore'),
  ('ore', 4010005, 5000, 1, 3, 120, 1, 'Orihalcon Ore'),
  ('ore', 4010006, 5000, 1, 3, 120, 1, 'Gold Ore'),
  ('ore', 4011000, 25000, 1, 3, 80, 1, 'Bronze Plate'),
  ('ore', 4011001, 25000, 1, 3, 80, 1, 'Steel Plate'),
  ('ore', 4011002, 35000, 1, 3, 60, 1, 'Mithril Plate'),
  ('ore', 4011003, 35000, 1, 3, 60, 1, 'Adamantium Plate'),
  ('ore', 4011004, 35000, 1, 3, 60, 1, 'Silver Plate'),
  ('ore', 4011005, 55000, 1, 2, 40, 1, 'Orihalcon Plate'),
  ('ore', 4011006, 55000, 1, 2, 40, 1, 'Gold Plate'),
  ('ore', 4020000, 3000, 1, 5, 200, 1, 'Garnet Ore'),
  ('ore', 4020001, 3000, 1, 5, 200, 1, 'Amethyst Ore'),
  ('ore', 4020002, 3000, 1, 5, 200, 1, 'AquaMarine Ore'),
  ('ore', 4020003, 3000, 1, 5, 200, 1, 'Emerald Ore'),
  ('ore', 4020004, 5000, 1, 3, 150, 1, 'Opal Ore'),
  ('ore', 4020005, 5000, 1, 3, 150, 1, 'Sapphire Ore'),
  ('ore', 4020006, 5000, 1, 3, 150, 1, 'Topaz Ore'),
  ('ore', 4020007, 10000, 1, 3, 80, 1, 'Diamond Ore'),
  ('ore', 4020008, 10000, 1, 3, 80, 1, 'Black Crystal Ore'),
  ('ore', 4021000, 35000, 1, 3, 60, 1, 'Garnet'),
  ('ore', 4021001, 35000, 1, 3, 60, 1, 'Amethyst'),
  ('ore', 4021002, 35000, 1, 3, 60, 1, 'AquaMarine'),
  ('ore', 4021003, 35000, 1, 3, 60, 1, 'Emerald'),
  ('ore', 4021004, 55000, 1, 3, 50, 1, 'Opal'),
  ('ore', 4021005, 55000, 1, 3, 50, 1, 'Sapphire'),
  ('ore', 4021006, 55000, 1, 3, 50, 1, 'Topaz'),
  ('ore', 4021007, 120000, 1, 2, 30, 1, 'Diamond'),
  ('ore', 4021008, 120000, 1, 2, 30, 1, 'Black Crystal'),
  ('ore', 4006000, 10000, 1, 5, 100, 1, 'The Magic Rock'),
  ('ore', 4006001, 10000, 1, 5, 100, 1, 'The Summoning Rock');

-- 装备 37 条（price = WZ price × 2）
INSERT IGNORE INTO `bot_sell_item_pool`
  (`category`, `item_id`, `price`, `qty_min`, `qty_max`, `weight`, `enabled`, `note`)
VALUES
  ('equip', 1302011, 300000, 1, 1, 100, 1, 'Neocora'),
  ('equip', 1312009, 380000, 1, 1, 100, 1, 'Hawkhead'),
  ('equip', 1322018, 380000, 1, 1, 100, 1, 'Tamus'),
  ('equip', 1332015, 500000, 1, 1, 100, 1, 'Deadly Fin'),
  ('equip', 1372008, 360000, 1, 1, 100, 1, 'Hinomaru Fan'),
  ('equip', 1382039, 460000, 1, 1, 100, 1, 'Maple Wisdom Staff'),
  ('equip', 1402011, 460000, 1, 1, 100, 1, 'Sparta'),
  ('equip', 1412007, 380000, 1, 1, 100, 1, 'The Shining'),
  ('equip', 1422009, 380000, 1, 1, 100, 1, 'The Blessing'),
  ('equip', 1432006, 380000, 1, 1, 100, 1, 'Holy Spear'),
  ('equip', 1442010, 500000, 1, 1, 100, 1, 'Skylar'),
  ('equip', 1462008, 480000, 1, 1, 100, 1, 'Golden Crow'),
  ('equip', 1472022, 500000, 1, 1, 100, 1, 'Bronze Gigantic'),
  ('equip', 1482008, 480000, 1, 1, 100, 1, 'Psycho Claw'),
  ('equip', 1492008, 500000, 1, 1, 100, 1, 'Burning Hell'),
  ('equip', 1002091, 240000, 1, 1, 100, 1, 'Dark Dome'),
  ('equip', 1002656, 110000, 1, 1, 100, 1, 'White Identity'),
  ('equip', 1002637, 170000, 1, 1, 100, 1, 'Black Pirate''s Bandana'),
  ('equip', 1040087, 150000, 1, 1, 100, 1, 'Blue Shouldermail'),
  ('equip', 1040091, 180000, 1, 1, 100, 1, 'Red Orientican'),
  ('equip', 1040105, 200000, 1, 1, 100, 1, 'Brown Studded Top'),
  ('equip', 1060076, 120000, 1, 1, 100, 1, 'Blue Shouldermail Pants'),
  ('equip', 1060080, 150000, 1, 1, 100, 1, 'Red Orientican Pants'),
  ('equip', 1061096, 180000, 1, 1, 100, 1, 'Aqua Platina Pants'),
  ('equip', 1050045, 180000, 1, 1, 100, 1, 'Blue Calas'),
  ('equip', 1051052, 240000, 1, 1, 100, 1, 'Blue Requierre'),
  ('equip', 1072132, 110000, 1, 1, 100, 1, 'Emerald Hildon Boots'),
  ('equip', 1072144, 130000, 1, 1, 100, 1, 'Red Gore Boots'),
  ('equip', 1072155, 170000, 1, 1, 100, 1, 'Purple Carzen Boots'),
  ('equip', 1082011, 220000, 1, 1, 100, 1, 'Gold Brist'),
  ('equip', 1082059, 240000, 1, 1, 100, 1, 'Bronze Clench'),
  ('equip', 1082222, 300000, 1, 1, 100, 1, 'Mechanical Glove'),
  ('equip', 1092004, 300000, 1, 1, 100, 1, 'Skull Shield'),
  ('equip', 1092016, 420000, 1, 1, 100, 1, 'Silver Ancient Shield'),
  ('equip', 1102000, 100000, 1, 1, 100, 1, 'Green Napoleon'),
  ('equip', 1102013, 140000, 1, 1, 100, 1, 'White Justice Cape'),
  ('equip', 1102022, 280000, 1, 1, 100, 1, 'Red Gaia Cape');
