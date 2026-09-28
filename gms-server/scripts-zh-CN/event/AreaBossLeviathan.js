/*
This file is part of the OdinMS Maple Story Server
Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
Matthias Butz <matze@odinms.de>
Jan Christian Meyer <vimes@odinms.de>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as
published by the Free Software Foundation version 3 as published by
the Free Software Foundation. You may not use, modify or distribute
this program under any other version of the GNU Affero General Public
License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

const Point = Java.type('java.awt.Point');
const LifeFactory = Java.type('org.gms.server.life.LifeFactory');
const PacketCreator = Java.type('org.gms.util.PacketCreator');
const LoggerFactory = Java.type('org.slf4j.LoggerFactory');
var log = null;
var channel = null;
var isinit = false;

var MapID = 240040401;
var BossID = 8220003;
var BossName = "大海兽";
/**刷新时间，分钟;  Generation time in minutes*/
var BossTime = 180;
/**指定Boss刷新的XY坐标位置; Specify the XY coordinate position for Boss refresh*/
var point = new Point(Math.floor((Math.random() * 600) - 300), 1125);
var BossNotice= "昂首而起的刹那，裹挟着冰晶的寒风如远古战吼般撕裂云层‌";

const methodName = "start";     //指定当前事件刷新Boss的函数，无需改动
/**
 -- Odin JavaScript --------------------------------------------------------------------------------
 Zeno Spawner
 -- Edited by --------------------------------------------------------------------------------------
 ThreeStep - based on xQuasar's King Clang spawner
 **/
function init() {
    em.setProperty("bossMobId", BossID);
    em.setProperty("bossMapId", MapID);
    channel = em.getChannelServer().getId();
    log = LoggerFactory.getLogger(em.getName());
  
    scheduleNew();
}

/** 计算下一次检查的间隔(毫秒)：数据库配置优先，含禁用兜底、每分钟下限与全局倍率 */
function getIntervalMillis() {
    var cfg = em.getBossRespawnConfig();
    // 已禁用：1 分钟后再检查一次，改回启用后能较快恢复
    if (cfg != null && !cfg.isEnabled()) {
        return 60 * 1000;
    }
    // 刷新间隔(分钟)：配置 <=0 时用脚本的 BossTime；getBossTime 会再乘一次全局倍率
    var minutes = BossTime;
    if (cfg != null && cfg.getSpawnInterval() != null && cfg.getSpawnInterval() > 0) {
        minutes = cfg.getSpawnInterval();
    }
    var Timer = em.getBossTime(minutes * 60 * 1000);  //转为毫秒并加载时间倍率修正
    // 兜底：倍率过小会把间隔抹成0，形成0延迟排期忙等
    return Timer < 60 * 1000 ? 60 * 1000 : Timer;
}

/** 服务器启动时生成：立即检查一次，缺了就立即补上 */
function scheduleNew() {
    setupTask = em.schedule(methodName, 0);
}

/** 配置热更新后重建排期：只按最新配置排下一拍，不立即检查、不立即生成Boss */
function rescheduleNew() {
    setupTask = em.schedule(methodName, getIntervalMillis());
}

function cancelSchedule() {
    if (setupTask != null) {
        setupTask.cancel(true);
    }
}

function start() {
    var graysPrairie = em.getChannelServer().getMapFactory().getMap(MapID);
    // ↓↓↓ 数据库覆盖项（改库即生效）：读不到配置则全部回落到脚本顶部常量 ↓↓↓
    var cfg = em.getBossRespawnConfig();

    // 已禁用：不生成也不再排期，1 分钟后再检查一次，改回启用后能较快恢复
    if (cfg != null && !cfg.isEnabled()) {
        em.schedule(methodName, 60 * 1000);
        return;
    }

    var Timer = getIntervalMillis();

    // 坐标与公告用局部变量承载，不直接改脚本级 point/BossNotice，
    // 否则清空数据库覆盖项后无法回到脚本默认值
    var bossPoint = point;
    if (cfg != null && cfg.getPointX() != null && cfg.getPointY() != null) {
        bossPoint = new Point(cfg.getPointX(), cfg.getPointY());
    }
    var bossNotice = BossNotice;
    if (cfg != null && cfg.getNotice()) {
        bossNotice = cfg.getNotice();
    }

    if (graysPrairie.getMonsterById(BossID) != null) {
        em.schedule(methodName, Timer);
        return;
    }
    const BossObj = LifeFactory.getMonster(BossID);
    BossName = BossObj.getName() || BossName;
    try {
        graysPrairie.spawnMonsterOnGroundBelow(BossObj, bossPoint);
        if(isinit) {
            log.info(`[事件脚本-野外BOSS] ${em.getName()} 已在频道 ${channel} 的 ${graysPrairie.getMapName()}(${MapID}) ${bossPoint.x} , ${bossPoint.y}) 生成 ${BossName}(${BossID})，检测间隔：${Timer / 60 / 1000} 分钟`);
        } else {
            isinit = true;
        }
    } catch (e) {
        console.error(`[事件脚本-野外BOSS] ${em.getName()} 在频道 ${channel} 的 ${graysPrairie.getMapName()}(${MapID}) ${bossPoint.x} , ${bossPoint.y}) 生成 ${BossName}(${BossID}) 时出错`,e);
    }
    graysPrairie.broadcastMessage(PacketCreator.serverNotice(6, `[野外BOSS] ${BossName}  ${bossNotice}`));     //聊天框输出当前地图范围的Boss登场消息

    em.schedule(methodName, Timer);
}

// ---------- FILLER FUNCTIONS ----------

function dispose() {}

function setup(eim, leaderid) {}

function monsterValue(eim, mobid) {return 0;}

function disbandParty(eim, player) {}

function playerDisconnected(eim, player) {}

function playerEntry(eim, player) {}

function monsterKilled(mob, eim) {}

function scheduledTimeout(eim) {}

function afterSetup(eim) {}

function changedLeader(eim, leader) {}

function playerExit(eim, player) {}

function leftParty(eim, player) {}

function clearPQ(eim) {}

function allMonstersDead(eim) {}

function playerUnregistered(eim, player) {}
