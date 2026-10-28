-- =============================================================================
-- 机器人（Bot）基础表：机器人档案 + 装备随机池
-- 装备池数据由 SoloMapling 的 GenericEquipPool.yaml / NXItemPool.yaml /
-- EquipOmitList.yaml 一次性导出，之后只从数据库读取，不再有 YAML 回退。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `bot_profile` (
  `id` INT(11) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `character_id` INT(11) NOT NULL COMMENT 'characters.id，真实角色行',
  `account_id` INT(11) NOT NULL COMMENT 'accounts.id，机器人专用账号',
  `map_id` INT(11) NOT NULL DEFAULT 910000000 COMMENT '出生地图',
  `spawn_x` INT(11) NOT NULL DEFAULT 0 COMMENT '出生点 X',
  `spawn_y` INT(11) NOT NULL DEFAULT 0 COMMENT '出生点 Y',
  `auto_start` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=开机自动上线',
  `bot_type` VARCHAR(32) NOT NULL DEFAULT 'IDLE' COMMENT '行为类型，本轮固定 IDLE',
  `note` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bot_profile_character` (`character_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机器人档案';

CREATE TABLE IF NOT EXISTS `bot_equip_generic_pool` (
  `id` INT(11) NOT NULL AUTO_INCREMENT,
  `category` VARCHAR(32) NOT NULL COMMENT 'tops/bottoms/overalls/weapons/caps/shoes/gloves/capes',
  `item_id` INT(11) NOT NULL COMMENT '装备物品 ID',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_generic_pool_cat_item` (`category`, `item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机器人通用装备随机池';

CREATE TABLE IF NOT EXISTS `bot_equip_nx_pool` (
  `id` INT(11) NOT NULL AUTO_INCREMENT,
  `category` VARCHAR(32) NOT NULL COMMENT 'caps/tops/bottoms/overalls/weapons/shoes/gloves/capes/earrings/face/eye/rings',
  `item_id` INT(11) NOT NULL COMMENT '点装物品 ID',
  `gender` VARCHAR(10) NOT NULL DEFAULT '' COMMENT 'male/female/unisex，空串=按物品 ID 第四位推导',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_nx_pool_cat_item` (`category`, `item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机器人 NX 点装随机池';

CREATE TABLE IF NOT EXISTS `bot_equip_omit_list` (
  `id` INT(11) NOT NULL AUTO_INCREMENT,
  `block_type` VARCHAR(8) NOT NULL DEFAULT 'ID' COMMENT 'ID=单条；RANGE=闭区间',
  `item_id` INT(11) NOT NULL DEFAULT 0 COMMENT 'block_type=ID 时生效',
  `range_start` INT(11) NOT NULL DEFAULT 0 COMMENT 'block_type=RANGE 时生效',
  `range_end` INT(11) NOT NULL DEFAULT 0 COMMENT 'block_type=RANGE 时生效',
  `note` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '物品名/说明',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_omit_pool_item` (`block_type`, `item_id`, `range_start`, `range_end`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机器人禁止自动穿戴的装备';

-- ----------------------------------------------------------------------------
-- 种子：通用装备池 127 条
-- ----------------------------------------------------------------------------
INSERT INTO `bot_equip_generic_pool` (`category`, `item_id`) VALUES ('tops', 1040002), ('tops', 1040006), ('tops', 1040010), ('tops', 1040013), ('tops', 1041002), ('tops', 1041004), ('tops', 1041005), ('tops', 1041006), ('bottoms', 1060002), ('bottoms', 1060004), ('bottoms', 1060007), ('bottoms', 1061002), ('bottoms', 1061039), ('bottoms', 1062000), ('bottoms', 1062001), ('overalls', 1050018), ('overalls', 1050100), ('overalls', 1050127), ('overalls', 1051017), ('overalls', 1051098), ('overalls', 1051140), ('weapons', 1302000), ('weapons', 1302013), ('weapons', 1302016), ('weapons', 1302017), ('weapons', 1302021), ('weapons', 1302022), ('weapons', 1302024), ('weapons', 1302025), ('weapons', 1302026), ('weapons', 1302027), ('weapons', 1302028), ('weapons', 1302029), ('weapons', 1302031), ('weapons', 1302033), ('weapons', 1302034), ('weapons', 1302049), ('weapons', 1302063), ('weapons', 1302065), ('weapons', 1302066), ('weapons', 1302071), ('weapons', 1302073), ('weapons', 1302074), ('weapons', 1302080), ('weapons', 1302094), ('weapons', 1302095), ('weapons', 1302098), ('weapons', 1312013), ('weapons', 1322021), ('weapons', 1322022);
INSERT INTO `bot_equip_generic_pool` (`category`, `item_id`) VALUES ('weapons', 1322027), ('weapons', 1322051), ('weapons', 1322063), ('weapons', 1332007), ('weapons', 1332020), ('weapons', 1332029), ('weapons', 1332030), ('weapons', 1332032), ('weapons', 1332053), ('weapons', 1402013), ('weapons', 1402014), ('weapons', 1402029), ('weapons', 1432008), ('weapons', 1432009), ('weapons', 1432013), ('weapons', 1432014), ('weapons', 1432015), ('weapons', 1432016), ('weapons', 1432017), ('weapons', 1432018), ('weapons', 1432039), ('weapons', 1442011), ('weapons', 1442012), ('weapons', 1442013), ('weapons', 1442014), ('weapons', 1442015), ('weapons', 1442016), ('weapons', 1442017), ('weapons', 1442018), ('weapons', 1442021), ('weapons', 1442022), ('weapons', 1442023), ('weapons', 1442026), ('weapons', 1442027), ('weapons', 1442028), ('weapons', 1442029), ('weapons', 1442030), ('caps', 1002001), ('caps', 1002006), ('caps', 1002012), ('caps', 1002019), ('caps', 1002020), ('caps', 1002026), ('caps', 1002060), ('caps', 1002066), ('caps', 1002080), ('caps', 1002081), ('caps', 1002082), ('caps', 1002083), ('caps', 1002089);
INSERT INTO `bot_equip_generic_pool` (`category`, `item_id`) VALUES ('caps', 1002090), ('caps', 1002357), ('shoes', 1072001), ('shoes', 1072012), ('shoes', 1072054), ('shoes', 1072055), ('shoes', 1072056), ('shoes', 1072238), ('shoes', 1072239), ('gloves', 1082002), ('gloves', 1082145), ('gloves', 1082146), ('gloves', 1082147), ('gloves', 1082148), ('gloves', 1082149), ('gloves', 1082150), ('gloves', 1082223), ('capes', 1102053), ('capes', 1102054), ('capes', 1102040), ('capes', 1102041), ('capes', 1102042), ('capes', 1102043), ('capes', 1102021), ('capes', 1102022), ('capes', 1102023), ('capes', 1102024);

-- ----------------------------------------------------------------------------
-- 种子：NX 点装池 169 条
-- ----------------------------------------------------------------------------
INSERT INTO `bot_equip_nx_pool` (`category`, `item_id`, `gender`) VALUES ('caps', 1000003, ''), ('caps', 1000018, ''), ('caps', 1000023, ''), ('caps', 1000025, ''), ('caps', 1000026, ''), ('caps', 1001002, ''), ('caps', 1001003, ''), ('caps', 1001004, ''), ('caps', 1001008, ''), ('caps', 1001009, ''), ('caps', 1001010, ''), ('caps', 1001020, ''), ('caps', 1001021, ''), ('caps', 1001036, ''), ('caps', 1001037, ''), ('caps', 1001038, ''), ('caps', 1001031, ''), ('caps', 1001032, ''), ('caps', 1002015, ''), ('caps', 1002031, ''), ('caps', 1002070, ''), ('caps', 1002071, ''), ('caps', 1002076, ''), ('caps', 1002077, ''), ('caps', 1002186, ''), ('caps', 1002187, ''), ('caps', 1002191, ''), ('caps', 1002192, ''), ('caps', 1002220, ''), ('caps', 1002225, ''), ('caps', 1002226, ''), ('caps', 1002227, ''), ('caps', 1002228, ''), ('caps', 1002229, ''), ('caps', 1002232, ''), ('caps', 1002233, ''), ('caps', 1002234, ''), ('caps', 1002235, ''), ('caps', 1002236, ''), ('caps', 1002237, ''), ('caps', 1002240, ''), ('caps', 1002293, ''), ('caps', 1002294, ''), ('caps', 1002295, ''), ('caps', 1002296, ''), ('caps', 1002297, ''), ('caps', 1002311, ''), ('caps', 1002312, ''), ('caps', 1002313, ''), ('caps', 1002314, '');
INSERT INTO `bot_equip_nx_pool` (`category`, `item_id`, `gender`) VALUES ('caps', 1002315, ''), ('caps', 1002316, ''), ('caps', 1002317, ''), ('caps', 1002318, ''), ('caps', 1002333, ''), ('caps', 1002334, ''), ('caps', 1002335, ''), ('caps', 1002336, ''), ('caps', 1002337, ''), ('caps', 1002352, ''), ('caps', 1002353, ''), ('caps', 1002354, ''), ('caps', 1002355, ''), ('caps', 1002356, ''), ('caps', 1002367, ''), ('caps', 1002368, ''), ('caps', 1002369, ''), ('caps', 1002370, ''), ('caps', 1002387, ''), ('caps', 1002396, ''), ('caps', 1002397, ''), ('caps', 1002412, ''), ('caps', 1002413, ''), ('caps', 1002414, ''), ('caps', 1002415, ''), ('caps', 1002416, ''), ('caps', 1002417, ''), ('caps', 1002426, ''), ('caps', 1002427, ''), ('caps', 1002428, ''), ('caps', 1002429, ''), ('caps', 1002431, ''), ('caps', 1002432, ''), ('caps', 1002433, ''), ('caps', 1002434, ''), ('caps', 1002435, ''), ('caps', 1002482, ''), ('caps', 1002483, ''), ('caps', 1002484, ''), ('caps', 1002485, ''), ('caps', 1002493, ''), ('caps', 1002495, ''), ('caps', 1002496, ''), ('caps', 1002501, ''), ('caps', 1002502, ''), ('caps', 1002503, ''), ('caps', 1002504, ''), ('caps', 1002534, ''), ('caps', 1002536, ''), ('caps', 1002542, '');
INSERT INTO `bot_equip_nx_pool` (`category`, `item_id`, `gender`) VALUES ('caps', 1002543, ''), ('caps', 1002544, ''), ('caps', 1002545, ''), ('caps', 1002548, ''), ('caps', 1002549, ''), ('caps', 1002552, ''), ('caps', 1002554, ''), ('caps', 1002555, ''), ('caps', 1002556, ''), ('caps', 1002557, ''), ('caps', 1002558, ''), ('caps', 1002559, ''), ('caps', 1002560, ''), ('caps', 1002562, ''), ('caps', 1002565, ''), ('caps', 1002566, ''), ('caps', 1002567, ''), ('caps', 1002568, ''), ('caps', 1002569, ''), ('caps', 1002575, ''), ('caps', 1002576, ''), ('caps', 1002695, ''), ('caps', 1002696, ''), ('caps', 1002697, ''), ('caps', 1002698, ''), ('caps', 1002699, ''), ('caps', 1002700, ''), ('caps', 1002721, ''), ('caps', 1002722, ''), ('caps', 1002723, ''), ('caps', 1002724, ''), ('caps', 1002725, ''), ('caps', 1002726, ''), ('caps', 1002727, ''), ('caps', 1002735, ''), ('caps', 1002736, ''), ('caps', 1002737, ''), ('caps', 1002738, ''), ('caps', 1002752, ''), ('caps', 1002753, ''), ('caps', 1002754, ''), ('caps', 1002755, ''), ('caps', 1002756, ''), ('caps', 1002774, ''), ('caps', 1002822, ''), ('caps', 1002823, ''), ('caps', 1002824, ''), ('caps', 1002831, ''), ('caps', 1002834, ''), ('caps', 1002835, '');
INSERT INTO `bot_equip_nx_pool` (`category`, `item_id`, `gender`) VALUES ('caps', 1002836, ''), ('caps', 1002837, ''), ('caps', 1002839, ''), ('caps', 1002840, ''), ('caps', 1002844, ''), ('caps', 1002845, ''), ('caps', 1002847, ''), ('caps', 1002849, ''), ('caps', 1002960, ''), ('caps', 1002961, ''), ('caps', 1002962, ''), ('caps', 1002968, ''), ('caps', 1002969, ''), ('caps', 1002970, ''), ('caps', 1002971, ''), ('caps', 1002973, ''), ('caps', 1002974, ''), ('caps', 1002976, ''), ('caps', 1002978, '');

-- ----------------------------------------------------------------------------
-- 种子：排除表 144 条 ID + 0 条区间
-- ----------------------------------------------------------------------------
INSERT INTO `bot_equip_omit_list` VALUES (1, 'ID', 1302033, 0, 0, '枫叶旗', 1);
INSERT INTO `bot_equip_omit_list` VALUES (2, 'ID', 1302035, 0, 0, '枫叶旗：1000天', 1);
INSERT INTO `bot_equip_omit_list` VALUES (3, 'ID', 1302036, 0, 0, '枫叶旗：1000天', 1);
INSERT INTO `bot_equip_omit_list` VALUES (4, 'ID', 1302065, 0, 0, '蓝色枫叶旗', 1);
INSERT INTO `bot_equip_omit_list` VALUES (5, 'ID', 1302066, 0, 0, '枫叶3周年旗', 1);
INSERT INTO `bot_equip_omit_list` VALUES (6, 'ID', 1302067, 0, 0, '枫叶庆典旗', 1);
INSERT INTO `bot_equip_omit_list` VALUES (7, 'ID', 1312033, 0, 0, '枫叶3周年旗', 1);
INSERT INTO `bot_equip_omit_list` VALUES (8, 'ID', 1322055, 0, 0, '枫叶3周年旗', 1);
INSERT INTO `bot_equip_omit_list` VALUES (9, 'ID', 1332057, 0, 0, '枫叶3周年旗', 1);
INSERT INTO `bot_equip_omit_list` VALUES (10, 'ID', 1382040, 0, 0, '枫叶3周年旗', 1);
INSERT INTO `bot_equip_omit_list` VALUES (11, 'ID', 1402040, 0, 0, '枫叶3周年旗', 1);
INSERT INTO `bot_equip_omit_list` VALUES (12, 'ID', 1412028, 0, 0, '枫叶3周年旗', 1);
INSERT INTO `bot_equip_omit_list` VALUES (13, 'ID', 1422032, 0, 0, '枫叶3周年旗', 1);
INSERT INTO `bot_equip_omit_list` VALUES (14, 'ID', 1432041, 0, 0, '枫叶3周年旗', 1);
INSERT INTO `bot_equip_omit_list` VALUES (15, 'ID', 1442052, 0, 0, '枫叶3周年旗', 1);
INSERT INTO `bot_equip_omit_list` VALUES (16, 'ID', 1452046, 0, 0, '枫叶3周年旗', 1);
INSERT INTO `bot_equip_omit_list` VALUES (17, 'ID', 1462041, 0, 0, '枫叶3周年旗', 1);
INSERT INTO `bot_equip_omit_list` VALUES (18, 'ID', 1472056, 0, 0, '枫叶3周年旗', 1);
INSERT INTO `bot_equip_omit_list` VALUES (19, 'ID', 1302073, 0, 0, '新加坡国旗(初学者)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (20, 'ID', 1302074, 0, 0, '马来西亚国旗(初学者)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (21, 'ID', 1302075, 0, 0, '新加坡国旗(单手剑)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (22, 'ID', 1302076, 0, 0, '马来西亚国旗(单手剑)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (23, 'ID', 1312036, 0, 0, '马来西亚国旗(单手斧)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (24, 'ID', 1322057, 0, 0, '新加坡国旗(单手杖)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (25, 'ID', 1322058, 0, 0, '马来西亚国旗(单手杖)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (26, 'ID', 1332060, 0, 0, '新加坡国旗(短剑)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (27, 'ID', 1332061, 0, 0, '马来西亚国旗(短剑)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (28, 'ID', 1382043, 0, 0, '新加坡国旗(长杖)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (29, 'ID', 1382044, 0, 0, '马来西亚国旗(长杖)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (30, 'ID', 1402042, 0, 0, '新加坡国旗(双手剑)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (31, 'ID', 1412030, 0, 0, '新加坡国旗(双手斧)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (32, 'ID', 1412031, 0, 0, '马来西亚国旗(双手斧)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (33, 'ID', 1422034, 0, 0, '新加坡国旗(双手钝器)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (34, 'ID', 1432043, 0, 0, '新加坡国旗(枪)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (35, 'ID', 1432044, 0, 0, '马来西亚国旗(枪)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (36, 'ID', 1442058, 0, 0, '新加坡国旗(矛)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (37, 'ID', 1452049, 0, 0, '新加坡国旗(弓)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (38, 'ID', 1452050, 0, 0, '马来西亚国旗(弓)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (39, 'ID', 1462044, 0, 0, '新加坡国旗(弩)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (40, 'ID', 1462045, 0, 0, '马来西亚国旗(弩)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (41, 'ID', 1472059, 0, 0, '新加坡国旗(拳套)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (42, 'ID', 1472060, 0, 0, '马来西亚国旗(拳套)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (43, 'ID', 1002500, 0, 0, '太极旗头巾', 1);
INSERT INTO `bot_equip_omit_list` VALUES (44, 'ID', 1051143, 0, 0, '太极旗套服', 1);
INSERT INTO `bot_equip_omit_list` VALUES (45, 'ID', 1102019, 0, 0, '太极披风', 1);
INSERT INTO `bot_equip_omit_list` VALUES (46, 'ID', 1102152, 0, 0, '黑骷髅海盗旗', 1);
INSERT INTO `bot_equip_omit_list` VALUES (47, 'ID', 1302105, 0, 0, '圣诞六翼天使武器(单手剑)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (48, 'ID', 1312039, 0, 0, '圣诞六翼天使武器(单手斧)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (49, 'ID', 1322065, 0, 0, '圣诞六翼天使武器(单手钝器)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (50, 'ID', 1332081, 0, 0, '圣诞六翼天使武器(短剑)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (51, 'ID', 1372046, 0, 0, '圣诞六翼天使武器(短杖)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (52, 'ID', 1382062, 0, 0, '圣诞六翼天使武器(长杖)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (53, 'ID', 1402053, 0, 0, '圣诞六翼天使武器(双手剑)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (54, 'ID', 1412035, 0, 0, '圣诞六翼天使武器(双手斧)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (55, 'ID', 1422039, 0, 0, '圣诞六翼天使武器(双手钝器)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (56, 'ID', 1432050, 0, 0, '圣诞六翼天使武器(枪)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (57, 'ID', 1442071, 0, 0, '圣诞六翼天使武器(矛)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (58, 'ID', 1452062, 0, 0, '圣诞六翼天使武器(弓)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (59, 'ID', 1462056, 0, 0, '圣诞六翼天使武器(弩)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (60, 'ID', 1472077, 0, 0, '圣诞六翼天使武器(拳套)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (61, 'ID', 1482029, 0, 0, '圣诞六翼天使武器(指节)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (62, 'ID', 1492030, 0, 0, '圣诞六翼天使武器(手枪)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (63, 'ID', 1302071, 0, 0, '粉红色花纹游泳圈(单手剑)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (64, 'ID', 1312034, 0, 0, '粉红色花纹游泳圈(单手斧)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (65, 'ID', 1322056, 0, 0, '粉红色花纹游泳圈(单手钝器)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (66, 'ID', 1332059, 0, 0, '粉红色花纹游泳圈(短剑)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (67, 'ID', 1382042, 0, 0, '粉红色花纹游泳圈(长杖)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (68, 'ID', 1402041, 0, 0, '粉红色花纹游泳圈(双手剑)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (69, 'ID', 1412029, 0, 0, '粉红色花纹游泳圈(双手斧)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (70, 'ID', 1422033, 0, 0, '粉红色花纹游泳圈(双手钝器)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (71, 'ID', 1432042, 0, 0, '粉红色花纹游泳圈(枪)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (72, 'ID', 1442053, 0, 0, '粉红色花纹游泳圈(矛)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (73, 'ID', 1452048, 0, 0, '粉红色花纹游泳圈(弓)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (74, 'ID', 1462043, 0, 0, '粉红色花纹游泳圈(弩)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (75, 'ID', 1472058, 0, 0, '粉红色花纹游泳圈(拳套)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (76, 'ID', 1482025, 0, 0, '粉红色花纹游泳圈(指节)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (77, 'ID', 1492026, 0, 0, '粉红色花纹游泳圈(手枪)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (78, 'ID', 1302112, 0, 0, '蝙蝠怪的汤勺', 1);
INSERT INTO `bot_equip_omit_list` VALUES (79, 'ID', 1302113, 0, 0, '蝙蝠怪的坲罗剑', 1);
INSERT INTO `bot_equip_omit_list` VALUES (80, 'ID', 1312042, 0, 0, '蝙蝠怪的花灵之斧', 1);
INSERT INTO `bot_equip_omit_list` VALUES (81, 'ID', 1312043, 0, 0, '蝙蝠怪的双利刃斧', 1);
INSERT INTO `bot_equip_omit_list` VALUES (82, 'ID', 1322068, 0, 0, '蝙蝠怪的钉头锤', 1);
INSERT INTO `bot_equip_omit_list` VALUES (83, 'ID', 1322069, 0, 0, '蝙蝠怪的毁灭之锤', 1);
INSERT INTO `bot_equip_omit_list` VALUES (84, 'ID', 1332084, 0, 0, '蝙蝠怪的小妖精刀', 1);
INSERT INTO `bot_equip_omit_list` VALUES (85, 'ID', 1332085, 0, 0, '蝙蝠怪的黑精灵拳刃', 1);
INSERT INTO `bot_equip_omit_list` VALUES (86, 'ID', 1332086, 0, 0, '蝙蝠怪的可撒之剑', 1);
INSERT INTO `bot_equip_omit_list` VALUES (87, 'ID', 1332087, 0, 0, '蝙蝠怪的霸杰之刃', 1);
INSERT INTO `bot_equip_omit_list` VALUES (88, 'ID', 1372050, 0, 0, '蝙蝠怪的圣令牌', 1);
INSERT INTO `bot_equip_omit_list` VALUES (89, 'ID', 1372051, 0, 0, '蝙蝠怪的凤凰短杖', 1);
INSERT INTO `bot_equip_omit_list` VALUES (90, 'ID', 1382066, 0, 0, '蝙蝠怪的魔灵之魂', 1);
INSERT INTO `bot_equip_omit_list` VALUES (91, 'ID', 1382067, 0, 0, '蝙蝠怪的怒涛之杖', 1);
INSERT INTO `bot_equip_omit_list` VALUES (92, 'ID', 1402056, 0, 0, '蝙蝠怪的青云剑', 1);
INSERT INTO `bot_equip_omit_list` VALUES (93, 'ID', 1402057, 0, 0, '蝙蝠怪的亚历山大之剑', 1);
INSERT INTO `bot_equip_omit_list` VALUES (94, 'ID', 1402058, 0, 0, '蝙蝠怪的斩魔刀', 1);
INSERT INTO `bot_equip_omit_list` VALUES (95, 'ID', 1402059, 0, 0, '蝙蝠怪的双手镭剑', 1);
INSERT INTO `bot_equip_omit_list` VALUES (96, 'ID', 1412038, 0, 0, '蝙蝠怪的大力神之斧', 1);
INSERT INTO `bot_equip_omit_list` VALUES (97, 'ID', 1412039, 0, 0, '蝙蝠怪的格洛斧', 1);
INSERT INTO `bot_equip_omit_list` VALUES (98, 'ID', 1422042, 0, 0, '蝙蝠怪的雷神之锤', 1);
INSERT INTO `bot_equip_omit_list` VALUES (99, 'ID', 1422043, 0, 0, '蝙蝠怪的镭奥钉锤', 1);
INSERT INTO `bot_equip_omit_list` VALUES (100, 'ID', 1432054, 0, 0, '蝙蝠怪的奥丁手戟', 1);
INSERT INTO `bot_equip_omit_list` VALUES (101, 'ID', 1432055, 0, 0, '蝙蝠怪的佛罗利刃', 1);
INSERT INTO `bot_equip_omit_list` VALUES (102, 'ID', 1442074, 0, 0, '蝙蝠怪的月神刀', 1);
INSERT INTO `bot_equip_omit_list` VALUES (103, 'ID', 1442075, 0, 0, '蝙蝠怪的飞翔斧', 1);
INSERT INTO `bot_equip_omit_list` VALUES (104, 'ID', 1452066, 0, 0, '蝙蝠怪的马蓝艾伦弓', 1);
INSERT INTO `bot_equip_omit_list` VALUES (105, 'ID', 1452067, 0, 0, '蝙蝠怪的火焰艾伦弓', 1);
INSERT INTO `bot_equip_omit_list` VALUES (106, 'ID', 1452068, 0, 0, '蝙蝠怪的黄金艾伦弓', 1);
INSERT INTO `bot_equip_omit_list` VALUES (107, 'ID', 1452069, 0, 0, '蝙蝠怪的黑色艾伦弓', 1);
INSERT INTO `bot_equip_omit_list` VALUES (108, 'ID', 1452070, 0, 0, '蝙蝠怪的梅杜斯', 1);
INSERT INTO `bot_equip_omit_list` VALUES (109, 'ID', 1462059, 0, 0, '蝙蝠怪的沧海弩弓', 1);
INSERT INTO `bot_equip_omit_list` VALUES (110, 'ID', 1462060, 0, 0, '蝙蝠怪的炎帝弩弓', 1);
INSERT INTO `bot_equip_omit_list` VALUES (111, 'ID', 1462061, 0, 0, '蝙蝠怪的金天弩弓', 1);
INSERT INTO `bot_equip_omit_list` VALUES (112, 'ID', 1462062, 0, 0, '蝙蝠怪的黑暗弩弓', 1);
INSERT INTO `bot_equip_omit_list` VALUES (113, 'ID', 1462063, 0, 0, '蝙蝠怪的可撒之弩', 1);
INSERT INTO `bot_equip_omit_list` VALUES (114, 'ID', 1472083, 0, 0, '蝙蝠怪的龙之拳', 1);
INSERT INTO `bot_equip_omit_list` VALUES (115, 'ID', 1472084, 0, 0, '蝙蝠怪的卡帝斯拳套', 1);
INSERT INTO `bot_equip_omit_list` VALUES (116, 'ID', 1482031, 0, 0, '蝙蝠怪的纱罗毁灭拳套', 1);
INSERT INTO `bot_equip_omit_list` VALUES (117, 'ID', 1482032, 0, 0, '蝙蝠怪的吸血鬼之爪', 1);
INSERT INTO `bot_equip_omit_list` VALUES (118, 'ID', 1492035, 0, 0, '蝙蝠怪的因弗蒂之枪', 1);
INSERT INTO `bot_equip_omit_list` VALUES (119, 'ID', 1492036, 0, 0, '蝙蝠怪的调停者', 1);
INSERT INTO `bot_equip_omit_list` VALUES (120, 'ID', 1372049, 0, 0, '扎昆的树枝', 1);
INSERT INTO `bot_equip_omit_list` VALUES (121, 'ID', 1382068, 0, 0, '贝音之翼', 1);
INSERT INTO `bot_equip_omit_list` VALUES (122, 'ID', 1402062, 0, 0, '贝音双手剑', 1);
INSERT INTO `bot_equip_omit_list` VALUES (123, 'ID', 1442078, 0, 0, '贝音矛', 1);
INSERT INTO `bot_equip_omit_list` VALUES (124, 'ID', 1452071, 0, 0, '贝音弓', 1);
INSERT INTO `bot_equip_omit_list` VALUES (125, 'ID', 1472086, 0, 0, '贝音拳套', 1);
INSERT INTO `bot_equip_omit_list` VALUES (126, 'ID', 1492037, 0, 0, '贝音短枪', 1);
INSERT INTO `bot_equip_omit_list` VALUES (127, 'ID', 1442079, 0, 0, '基本矛', 1);
INSERT INTO `bot_equip_omit_list` VALUES (128, 'ID', 1702212, 0, 0, '异界之器', 1);
INSERT INTO `bot_equip_omit_list` VALUES (129, 'ID', 1702216, 0, 0, '爱心礼物棒', 1);
INSERT INTO `bot_equip_omit_list` VALUES (130, 'ID', 1302133, 0, 0, '地狱大公单手剑', 1);
INSERT INTO `bot_equip_omit_list` VALUES (131, 'ID', 1332099, 0, 0, '地狱大公短剑', 1);
INSERT INTO `bot_equip_omit_list` VALUES (132, 'ID', 1372058, 0, 0, '地狱大公短杖', 1);
INSERT INTO `bot_equip_omit_list` VALUES (133, 'ID', 1382080, 0, 0, '地狱大公长杖', 1);
INSERT INTO `bot_equip_omit_list` VALUES (134, 'ID', 1402072, 0, 0, '地狱大公双手剑', 1);
INSERT INTO `bot_equip_omit_list` VALUES (135, 'ID', 1412046, 0, 0, '地狱大公斧', 1);
INSERT INTO `bot_equip_omit_list` VALUES (136, 'ID', 1432061, 0, 0, '地狱大公枪', 1);
INSERT INTO `bot_equip_omit_list` VALUES (137, 'ID', 1442103, 0, 0, '地狱大公矛', 1);
INSERT INTO `bot_equip_omit_list` VALUES (138, 'ID', 1452085, 0, 0, '地狱大公弓箭', 1);
INSERT INTO `bot_equip_omit_list` VALUES (139, 'ID', 1462075, 0, 0, '地狱大公弩', 1);
INSERT INTO `bot_equip_omit_list` VALUES (140, 'ID', 1472100, 0, 0, '地狱大公拳套', 1);
INSERT INTO `bot_equip_omit_list` VALUES (141, 'ID', 1482046, 0, 0, '地狱大公指节', 1);
INSERT INTO `bot_equip_omit_list` VALUES (142, 'ID', 1492048, 0, 0, '地狱大公短枪', 1);
INSERT INTO `bot_equip_omit_list` VALUES (143, 'ID', 1452083, 0, 0, '所罗门多变之弓(任务道具)', 1);
INSERT INTO `bot_equip_omit_list` VALUES (144, 'ID', 1452084, 0, 0, '所罗门之弓(任务道具)', 1);