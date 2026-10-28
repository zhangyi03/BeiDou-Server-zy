-- ============================================================================
-- 野外 Boss 刷新配置表 —— 全量快照（建表 + 全部 25 个野外 Boss）
-- ============================================================================
-- 本文件是野外 Boss 配置的**唯一 SQL 入口**：表结构 + 全部配置行都在这里。
-- 原先 V1.11.10 ~ V1.11.23 这 14 个增量迁移已合并进本文件并删除。
--
-- 设计要点：把刷新间隔、生成坐标、登场公告从事件脚本常量搬到数据库，
-- 便于按单个 Boss 调整，无需改脚本重编。
--
-- 为什么用 event_name（脚本名，不带 .js）当唯一键而不是 boss_id：
--   同一个 boss_id 会被多个脚本使用（提莫 5220003 曾占 Timer1/2/3，
--   浮士德 5220002 曾占 Faust1/2），用 boss_id 会直接撞键。
--   事件管理器本身也是按脚本名索引的，脚本侧连 BossID 都不用传。
--
-- ⚠️ event_name 必须与脚本文件名严格一致，即：
--      scripts{,-zh-CN}/event/<event_name>.js
--    改名 / 删除必须**脚本（中英两份）+ 本表行 + 本文件**三处同步。
--    漏改本表行 => 脚本读不到配置，静默回落到脚本顶部的常量（改间隔不生效，页面还显示得好好的）。
--    中英两个脚本目录都会被目录扫描注册（Channel.getEvents()），所以两个目录都要处理。
--
-- 字段留空即回落到脚本内的原有常量：
--   spawn_interval <= 0  -> 用脚本 BossTime
--   point_x IS NULL      -> 用脚本 point
--   notice IS NULL/''    -> 用脚本 BossNotice
-- 注意 spawn_interval 是「基准分钟数」，实际间隔还会乘一次全局配置
-- boss_respawn_mob_time_rate（脚本内 em.getBossTime 那一层）。
--
-- ⚠️ 生效需要重启后端（事件名单与脚本在启动时才扫描）。
-- ⚠️ enabled = 0 只是不再排期生成，脚本仍在事件名单里。
--
-- ----------------------------------------------------------------------------
-- 合并前的变更历史（原 V1.11.10 ~ V1.11.23，保留备查）
-- ----------------------------------------------------------------------------
--   V1.11.10  删 AreaBossTimer1 / AreaBossTimer3（提莫只留「时间漩涡」220050100）
--   V1.11.11  删 AreaBossCentipede（巨型蜈蚣 5220004）
--   V1.11.12  修 AreaBossDoor4 的 boss_name：牛魔王 -> 地狱大公（9400633 实为 Astaroth）
--   V1.11.13  AreaBossTimer2 -> AreaBossTimer
--   V1.11.14  删 AreaBossFaust1（浮士德只留巫婆森林Ⅱ 100040106）
--   V1.11.15  AreaBossFaust2 -> AreaBossFaust
--   V1.11.16  删 AreaBossDoor1~6（这 6 只改由 map/onUserEnter/677000XXX.js 生成）
--   V1.11.17  AreaBossEliza1 -> AreaBossEliza
--   V1.11.18  新增 AreaBossMushmom / AreaBossZombieMushmom
--   V1.11.19  新增 AreaBossJrBalrog
--   V1.11.20  新增 AreaBossSnowman
--   V1.11.21  新增 AreaBossGriffey / AreaBossManon
--   V1.11.22  新增 AreaBossBlueMushmom
--   V1.11.23  新增 AreaBossFemaleBoss
--
-- ----------------------------------------------------------------------------
-- ⚠️ 下列 8 个 Boss 的地图 WZ **原本自带原生 life 刷怪点**，已同步从 wz/Map.wz/ 删除
--    （删除随代码仓库生效，不在这份 SQL 里）。不删的话，原生 mobTime 会抢先放回 Boss，
--    脚本每次检查都发现"已在场"直接跳过 —— 数据库改间隔根本不生效：
--      6130101 蘑菇王         Map1/100000005.img.xml   (原 mobTime=1800)
--      6300005 僵尸蘑菇王     Map1/105070002.img.xml   (原 mobTime=1800)
--      8130100 蝙蝠怪         Map1/105090900.img.xml   (原 mobTime=10800，删后 life 段为空)
--      8220001 驮狼雪人       Map2/211040101.img.xml   (原 mobTime=600)
--      8180001 天鹰           Map2/240020101.img.xml   (原 mobTime=10800)
--      8180000 火焰龙/喷火龙  Map2/240020401.img.xml   (原 mobTime=10800)
--      9400205 蓝蘑菇王       Map8/800010100.img.xml   (原 mobTime=82800)
--      9400121 女老板         Map8/801040003.img.xml   (原 mobTime=14400)
--    新增野外 Boss 前必查：grep -rl '"<bossId>"' wz/Map.wz/
--      无命中 -> 直接加脚本；有命中 -> 必须先删掉那条 life 节点，让本表成为唯一控制。
--    （life 子索引不连续/乱序都没问题，loadLifeWz 遍历子节点、不按名字取索引。）
--
-- ⚠️ 6 只「XX藏身之地」的 Boss（黑暗独角兽 / 印第安老斑鸠 / 沃勒福 / 地狱大公 /
--    牛魔王 / 雪之猫女，地图 677000003/005/009/012/001/007）**不在本表内** ——
--    它们应该由 scripts{,-zh-CN}/map/onUserEnter/<mapId>.js 在玩家进入时生成，
--    与野外定时脚本无关（曾经的 AreaBossDoor1~6 是重复链路，已删）。
--
-- ----------------------------------------------------------------------------
-- 幂等说明
-- ----------------------------------------------------------------------------
-- 建表用 CREATE TABLE IF NOT EXISTS，数据用 INSERT IGNORE，重复执行安全：
--   - 新库：一条迁移即可得到完整的 25 行。
--   - 老库：表与数据都已存在，整份文件会被全部跳过，**不会覆盖你在后台页面调过的值**。
--   因此后续新增 Boss 时，除了改本文件，还需在库上手工执行一次那条 INSERT
--   （老库的 V1.11.9 已 applied，Flyway 不会重跑）。
-- ============================================================================

CREATE TABLE IF NOT EXISTS `boss_respawn_config`
(
    `id`             INT(11)      NOT NULL AUTO_INCREMENT COMMENT '自增id',
    `event_name`     VARCHAR(64)  NOT NULL COMMENT '事件脚本名（不带.js），如 AreaBossMano',
    `boss_id`        INT(11)      NOT NULL COMMENT '怪物ID',
    `map_id`         INT(11)      NOT NULL COMMENT '地图ID',
    `boss_name`      VARCHAR(64)  NULL     COMMENT '备注名，空则取怪物名',
    `spawn_interval` INT(11)      NOT NULL DEFAULT 180 COMMENT '刷新间隔(分钟)，<=0 用脚本默认值',
    `point_x`        INT(11)      NULL     COMMENT '生成X坐标，NULL 用脚本默认值',
    `point_y`        INT(11)      NULL     COMMENT '生成Y坐标，NULL 用脚本默认值',
    `notice`         VARCHAR(255) NULL     COMMENT '登场公告，NULL 或空 用脚本默认值',
    `enabled`        TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用，0=不再排期生成',
    `create_time`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    TIMESTAMP    NULL     ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_event_name` (`event_name`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  AUTO_INCREMENT = 1
  COMMENT '野外Boss刷新配置表';

-- ============================================================================
-- 全部 25 个野外 Boss（按 event_name 字母序）
-- 间隔统一 180 分钟（与脚本内 BossTime 一致）；坐标留空即用脚本内 point。
-- boss_name 只用于后台页面「名称」列展示，脚本一行都不读（脚本用自身 BossName 字面量）。
-- 注意脚本名按英文名、boss_name 填中文名，两者不一致是既有约定不是笔误。
-- ============================================================================
INSERT IGNORE INTO `boss_respawn_config` (`event_name`, `boss_id`, `map_id`, `boss_name`, `spawn_interval`)
VALUES
    -- ---- 原始种子（V1.11.9）中保留至今的 17 个 ----
    ('AreaBossBamboo', 6090002, 800020120, '青竹武士', 180),
    ('AreaBossDeo', 3220001, 260010201, '大宇', 180),
    ('AreaBossDyle', 6220000, 107000300, '多尔', 180),
    ('AreaBossEliza', 8220000, 200010300, '艾利杰', 180),          -- V1.11.17 由 Eliza1 改名
    ('AreaBossFaust', 5220002, 100040106, '浮士德', 180),          -- V1.11.15 由 Faust2 改名，只留巫婆森林Ⅱ
    ('AreaBossKimera', 8220002, 261030000, '吉米拉', 180),
    ('AreaBossKingClang', 5220001, 110040000, '巨居蟹', 180),
    ('AreaBossKingSageCat', 7220002, 250010504, '妖怪禅师', 180),
    ('AreaBossLeviathan', 8220003, 240040401, '大海兽', 180),
    ('AreaBossMano', 2220000, 104000400, '红蜗牛王', 180),         -- 注意与 AreaBossManon 只差一个 n
    ('AreaBossNineTailedFox', 7220001, 222010310, '九尾狐', 180),
    ('AreaBossSeruf', 4220001, 230020100, '歇尔夫', 180),
    ('AreaBossSnackBar', 8220009, 105090310, '小吃店', 180),
    ('AreaBossStumpy', 3220000, 101030404, '树妖王', 180),
    ('AreaBossTaeRoon', 7220000, 250010304, '肯德熊', 180),
    ('AreaBossTimer', 5220003, 220050100, '提莫', 180),            -- V1.11.13 由 Timer2 改名，只留时间漩涡
    ('AreaBossZeno', 6220001, 221040301, '朱诺', 180),

    -- ---- V1.11.18 新增：两只蘑菇王（均已删地图原生 life）----
    ('AreaBossMushmom', 6130101, 100000005, '蘑菇王', 180),
    ('AreaBossZombieMushmom', 6300005, 105070002, '僵尸蘑菇王', 180),

    -- ---- V1.11.19 新增 ----
    ('AreaBossJrBalrog', 8130100, 105090900, '蝙蝠怪', 180),

    -- ---- V1.11.20 新增 ----
    ('AreaBossSnowman', 8220001, 211040101, '驮狼雪人', 180),

    -- ---- V1.11.21 新增：神木村一对（均已删地图原生 life）----
    ('AreaBossGriffey', 8180001, 240020101, '天鹰', 180),
    ('AreaBossManon', 8180000, 240020401, '火焰龙', 180),          -- 俗称喷火龙，官方中文怪名「火焰龙」

    -- ---- V1.11.22 新增 ----
    ('AreaBossBlueMushmom', 9400205, 800010100, '蓝蘑菇王', 180),

    -- ---- V1.11.23 新增 ----
    ('AreaBossFemaleBoss', 9400121, 801040003, '女老板', 180);
