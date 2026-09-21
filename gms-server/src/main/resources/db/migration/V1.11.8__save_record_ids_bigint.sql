-- 存档内部记录 ID 扩容为 BIGINT，与物品记录的 Java long 读写保持一致。
-- 覆盖 12 个自增主键，以及装备、雇佣商店和遗留快递表的物品关联列。
-- 每列独立检查，已是 BIGINT（含 UNSIGNED）则跳过，兼容手工扩容和中断后重跑。
-- 保留现有 ID、自增进度、默认值、主键及索引；不清表、不重置 AUTO_INCREMENT。
-- 任务关联 queststatusid 由 V1.11.7__queststatusid_bigint.sql 负责。
-- 仅在获准的部署维护窗口执行：类型变更可能重建大表、阻塞写入，DDL 会隐式提交。

SET @save_id_bigint_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventoryitems'
          AND COLUMN_NAME = 'inventoryitemid' AND DATA_TYPE = 'bigint'),
    'DO 1',
    'ALTER TABLE `inventoryitems` MODIFY COLUMN `inventoryitemid` BIGINT NOT NULL AUTO_INCREMENT'
);
PREPARE save_id_bigint_stmt FROM @save_id_bigint_sql;
EXECUTE save_id_bigint_stmt;
DEALLOCATE PREPARE save_id_bigint_stmt;

SET @save_id_bigint_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventoryequipment'
          AND COLUMN_NAME = 'inventoryequipmentid' AND DATA_TYPE = 'bigint'),
    'DO 1',
    'ALTER TABLE `inventoryequipment` MODIFY COLUMN `inventoryequipmentid` BIGINT NOT NULL AUTO_INCREMENT'
);
PREPARE save_id_bigint_stmt FROM @save_id_bigint_sql;
EXECUTE save_id_bigint_stmt;
DEALLOCATE PREPARE save_id_bigint_stmt;

SET @save_id_bigint_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventoryequipment'
          AND COLUMN_NAME = 'inventoryitemid' AND DATA_TYPE = 'bigint'),
    'DO 1',
    'ALTER TABLE `inventoryequipment` MODIFY COLUMN `inventoryitemid` BIGINT NOT NULL DEFAULT 0'
);
PREPARE save_id_bigint_stmt FROM @save_id_bigint_sql;
EXECUTE save_id_bigint_stmt;
DEALLOCATE PREPARE save_id_bigint_stmt;

SET @save_id_bigint_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventorymerchant'
          AND COLUMN_NAME = 'inventoryitemid' AND DATA_TYPE = 'bigint'),
    'DO 1',
    'ALTER TABLE `inventorymerchant` MODIFY COLUMN `inventoryitemid` BIGINT NOT NULL DEFAULT 0'
);
PREPARE save_id_bigint_stmt FROM @save_id_bigint_sql;
EXECUTE save_id_bigint_stmt;
DEALLOCATE PREPARE save_id_bigint_stmt;

SET @save_id_bigint_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'dueyitems'
          AND COLUMN_NAME = 'inventoryitemid' AND DATA_TYPE = 'bigint'),
    'DO 1',
    'ALTER TABLE `dueyitems` MODIFY COLUMN `inventoryitemid` BIGINT NOT NULL DEFAULT 0'
);
PREPARE save_id_bigint_stmt FROM @save_id_bigint_sql;
EXECUTE save_id_bigint_stmt;
DEALLOCATE PREPARE save_id_bigint_stmt;

SET @save_id_bigint_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'keymap'
          AND COLUMN_NAME = 'id' AND DATA_TYPE = 'bigint'),
    'DO 1',
    'ALTER TABLE `keymap` MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT'
);
PREPARE save_id_bigint_stmt FROM @save_id_bigint_sql;
EXECUTE save_id_bigint_stmt;
DEALLOCATE PREPARE save_id_bigint_stmt;

SET @save_id_bigint_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skills'
          AND COLUMN_NAME = 'id' AND DATA_TYPE = 'bigint'),
    'DO 1',
    'ALTER TABLE `skills` MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT'
);
PREPARE save_id_bigint_stmt FROM @save_id_bigint_sql;
EXECUTE save_id_bigint_stmt;
DEALLOCATE PREPARE save_id_bigint_stmt;

SET @save_id_bigint_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'buddies'
          AND COLUMN_NAME = 'id' AND DATA_TYPE = 'bigint'),
    'DO 1',
    'ALTER TABLE `buddies` MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT'
);
PREPARE save_id_bigint_stmt FROM @save_id_bigint_sql;
EXECUTE save_id_bigint_stmt;
DEALLOCATE PREPARE save_id_bigint_stmt;

SET @save_id_bigint_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'skillmacros'
          AND COLUMN_NAME = 'id' AND DATA_TYPE = 'bigint'),
    'DO 1',
    'ALTER TABLE `skillmacros` MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT'
);
PREPARE save_id_bigint_stmt FROM @save_id_bigint_sql;
EXECUTE save_id_bigint_stmt;
DEALLOCATE PREPARE save_id_bigint_stmt;

SET @save_id_bigint_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'savedlocations'
          AND COLUMN_NAME = 'id' AND DATA_TYPE = 'bigint'),
    'DO 1',
    'ALTER TABLE `savedlocations` MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT'
);
PREPARE save_id_bigint_stmt FROM @save_id_bigint_sql;
EXECUTE save_id_bigint_stmt;
DEALLOCATE PREPARE save_id_bigint_stmt;

SET @save_id_bigint_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wishlists'
          AND COLUMN_NAME = 'id' AND DATA_TYPE = 'bigint'),
    'DO 1',
    'ALTER TABLE `wishlists` MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT'
);
PREPARE save_id_bigint_stmt FROM @save_id_bigint_sql;
EXECUTE save_id_bigint_stmt;
DEALLOCATE PREPARE save_id_bigint_stmt;

SET @save_id_bigint_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'questprogress'
          AND COLUMN_NAME = 'id' AND DATA_TYPE = 'bigint'),
    'DO 1',
    'ALTER TABLE `questprogress` MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT'
);
PREPARE save_id_bigint_stmt FROM @save_id_bigint_sql;
EXECUTE save_id_bigint_stmt;
DEALLOCATE PREPARE save_id_bigint_stmt;

SET @save_id_bigint_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'medalmaps'
          AND COLUMN_NAME = 'id' AND DATA_TYPE = 'bigint'),
    'DO 1',
    'ALTER TABLE `medalmaps` MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT'
);
PREPARE save_id_bigint_stmt FROM @save_id_bigint_sql;
EXECUTE save_id_bigint_stmt;
DEALLOCATE PREPARE save_id_bigint_stmt;

SET @save_id_bigint_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trocklocations'
          AND COLUMN_NAME = 'trockid' AND DATA_TYPE = 'bigint'),
    'DO 1',
    'ALTER TABLE `trocklocations` MODIFY COLUMN `trockid` BIGINT NOT NULL AUTO_INCREMENT'
);
PREPARE save_id_bigint_stmt FROM @save_id_bigint_sql;
EXECUTE save_id_bigint_stmt;
DEALLOCATE PREPARE save_id_bigint_stmt;

SET @save_id_bigint_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'area_info'
          AND COLUMN_NAME = 'id' AND DATA_TYPE = 'bigint'),
    'DO 1',
    'ALTER TABLE `area_info` MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT'
);
PREPARE save_id_bigint_stmt FROM @save_id_bigint_sql;
EXECUTE save_id_bigint_stmt;
DEALLOCATE PREPARE save_id_bigint_stmt;

