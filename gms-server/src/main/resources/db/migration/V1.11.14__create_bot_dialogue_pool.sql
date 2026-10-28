-- =============================================================================
-- 机器人对话池：摆摊名称 + 喊话内容
--
-- 两件事原来都硬编码在 BotShop 里（SHOP_DESCRIPTION 常量 + AD_LINES 数组），
-- 想换文案只能改代码重编译。本表把它们搬进数据库，管理端「机器人管理 → 对话池」
-- 直接增删改，保存后热重载，不必重启服务端。
--
-- pool_type：
--   shop_name  摆摊名称——开摊时抽一条当 PlayerShop 的摊位标题（自由市场里别人看到的那行字）
--   shout      喊话内容——摊主每隔 12~20 秒原地喊一句，同一个池里加权随机
--
-- weight 是同池抽签权重，越高越容易被抽到；enabled=0 的行保留但不参与抽取。
-- content 与 pool_type 组成唯一键，同一句话不会重复入库。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `bot_dialogue_pool` (
  `id` INT(11) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `pool_type` VARCHAR(16) NOT NULL COMMENT 'shop_name=摆摊名称 / shout=喊话内容',
  `content` VARCHAR(200) NOT NULL COMMENT '文案正文',
  `weight` INT(11) NOT NULL DEFAULT 100 COMMENT '抽签权重，越大越容易被抽到',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '1=参与抽取',
  `note` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '备注（仅后台展示）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bot_dialogue_pool` (`pool_type`, `content`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机器人对话池（摆摊名称 / 喊话内容）';

-- 摆摊名称：客户端摊位框一行显示，控制在 20 字以内比较好看
INSERT IGNORE INTO `bot_dialogue_pool` (`pool_type`, `content`, `weight`, `enabled`, `note`) VALUES
  ('shop_name', '卷轴小铺', 100, 1, '通用'),
  ('shop_name', '便宜卖装备', 100, 1, '通用'),
  ('shop_name', '矿石杂货铺', 100, 1, '通用'),
  ('shop_name', '强化材料专卖', 100, 1, '通用'),
  ('shop_name', '药水批发', 100, 1, '通用'),
  ('shop_name', '白板装备清仓', 80, 1, '通用'),
  ('shop_name', '高级矿石专卖', 80, 1, '通用'),
  ('shop_name', '卷轴药水一站齐', 100, 1, '通用'),
  ('shop_name', '飞镖铺子', 60, 1, '通用'),
  ('shop_name', '装备矿石补给站', 100, 1, '通用'),
  ('shop_name', '收摊前大甩卖', 60, 1, '通用'),
  ('shop_name', '新人套装便宜出', 60, 1, '通用'),
  ('shop_name', '精品装备小店', 80, 1, '通用'),
  ('shop_name', '老板讲价勿扰', 40, 1, '通用');

-- 喊话内容：摊主在自由市场里原地喊的广告词
INSERT IGNORE INTO `bot_dialogue_pool` (`pool_type`, `content`, `weight`, `enabled`, `note`) VALUES
  ('shout', '卷轴、药水、飞镖，走过路过别错过~', 100, 1, '通用'),
  ('shout', '新货上架，价格公道，老板来看看？', 100, 1, '通用'),
  ('shout', '收摊前最后一波特价，手慢无！', 80, 1, '通用'),
  ('shout', '强化卷轴齐全，欢迎选购~', 100, 1, '通用'),
  ('shout', '白板装备便宜出，矿石论堆卖！', 80, 1, '通用'),
  ('shout', '装备矿石都有，缺啥问我~', 100, 1, '通用'),
  ('shout', '全套强化材料，一次买齐~', 80, 1, '通用'),
  ('shout', '便宜到哭，不信进来看！', 60, 1, '通用'),
  ('shout', '长期收卷轴，价格好商量~', 40, 1, '通用'),
  ('shout', '摊位货全，翻翻看？', 100, 1, '通用'),
  ('shout', '新手补给站，装备药水都有~', 80, 1, '通用'),
  ('shout', '今天新到一批好货，先到先得！', 80, 1, '通用'),
  ('shout', '矿石管够，价格好说~', 80, 1, '通用'),
  ('shout', '有需要的直接买，不用问价~', 60, 1, '通用'),
  ('shout', '大佬们来照顾下生意~', 60, 1, '通用'),
  ('shout', '自由市场老店，品质保证~', 60, 1, '通用'),
  ('shout', '卷轴打包更便宜哦~', 60, 1, '通用'),
  ('shout', '卖完就收摊，欲购从速！', 60, 1, '通用');
