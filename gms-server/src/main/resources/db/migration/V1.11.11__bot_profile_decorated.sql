-- =============================================================================
-- 机器人装饰结果落库标记
--
-- 机器人按真实玩家持久化：首次上线时随机生成外观与装备，并把结果写进
-- characters（hair / face / skincolor / gender）与 inventoryitems + inventoryequipment，
-- 之后每次上线都直接读库，不再重新随机。
--
-- decorated = 1 表示「外观已固化」；此时若装备栏为空（被重置或人工清空），
-- 只会补装备、不会重新捏外观。
-- =============================================================================

ALTER TABLE `bot_profile`
  ADD COLUMN `decorated` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=外观已随机生成并落库',
  ADD COLUMN `bot_tier` VARCHAR(16) NOT NULL DEFAULT 'S' COMMENT '人设档 S/A/B/C/D，决定发型与装备池抽取范围';
