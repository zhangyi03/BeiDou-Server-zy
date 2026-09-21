-- 任务记录 ID 使用 BIGINT，与 Java long 读写保持一致。
-- 兼容正式环境已手动扩容、只扩容部分字段、DDL 成功后迁移记录未写入的情况。
-- 每个字段单独检查；已是 BIGINT（含 UNSIGNED）则保留现状，不重复重建表。
-- 保留现有 ID、主键、索引及自增，不清表或重置 AUTO_INCREMENT。
-- 待获准部署时由迁移流程执行；未扩容的表可能重建并阻塞写入，DDL 会隐式提交。

SET @quest_id_bigint_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'queststatus'
          AND COLUMN_NAME = 'queststatusid' AND DATA_TYPE = 'bigint'),
    'DO 1',
    'ALTER TABLE `queststatus` MODIFY COLUMN `queststatusid` BIGINT NOT NULL AUTO_INCREMENT'
);
PREPARE quest_id_bigint_stmt FROM @quest_id_bigint_sql;
EXECUTE quest_id_bigint_stmt;
DEALLOCATE PREPARE quest_id_bigint_stmt;

SET @quest_id_bigint_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'questprogress'
          AND COLUMN_NAME = 'queststatusid' AND DATA_TYPE = 'bigint'),
    'DO 1',
    'ALTER TABLE `questprogress` MODIFY COLUMN `queststatusid` BIGINT NOT NULL DEFAULT 0'
);
PREPARE quest_id_bigint_stmt FROM @quest_id_bigint_sql;
EXECUTE quest_id_bigint_stmt;
DEALLOCATE PREPARE quest_id_bigint_stmt;

SET @quest_id_bigint_sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'medalmaps'
          AND COLUMN_NAME = 'queststatusid' AND DATA_TYPE = 'bigint'),
    'DO 1',
    'ALTER TABLE `medalmaps` MODIFY COLUMN `queststatusid` BIGINT NOT NULL'
);
PREPARE quest_id_bigint_stmt FROM @quest_id_bigint_sql;
EXECUTE quest_id_bigint_stmt;
DEALLOCATE PREPARE quest_id_bigint_stmt;
