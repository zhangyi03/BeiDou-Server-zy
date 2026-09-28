import axios from 'axios';

/**
 * 野外Boss刷新配置（列表返回项）
 * 对应后端 org.gms.model.dto.BossRespawnConfigDTO
 */
export interface BossRespawnConfigResult {
  id: number;
  /** 事件脚本名，不带 .js 后缀，如 AreaBossMano，唯一键 */
  eventName: string;
  /** 怪物ID */
  bossId: number;
  /** 地图ID */
  mapId: number;
  /** 备注名，空则脚本取怪物名 */
  bossName: string | null;
  /** 刷新间隔(分钟)，<=0 表示沿用脚本内 BossTime */
  spawnInterval: number;
  /** 生成X坐标，null 表示沿用脚本内 point */
  pointX: number | null;
  /** 生成Y坐标，null 表示沿用脚本内 point */
  pointY: number | null;
  /** 登场公告，null 表示沿用脚本内 BossNotice */
  notice: string | null;
  /** 是否启用 */
  enabled: boolean;
  /** 实际生效间隔(分钟)，仅展示用：spawnInterval × timeRate；脚本默认值时为空 */
  effectiveMinutes: number | null;
  /** 当前全局刷新时间倍率 boss_respawn_mob_time_rate，仅展示用 */
  timeRate: number;
}

/**
 * 提交给后端的结构：只带后端真正消费的字段。
 * 坐标/公告用 null 表示"回落脚本默认值"，后端据此清空覆盖项。
 */
export interface BossRespawnConfigSubmit {
  eventName: string;
  bossId: number;
  mapId: number;
  bossName: string | null;
  spawnInterval: number;
  pointX: number | null;
  pointY: number | null;
  notice: string | null;
  enabled: boolean;
}

export function getBossRespawnConfigList() {
  return axios.get('/bossrespawn/v1/getConfigList');
}

export function updateBossRespawnConfig(data: BossRespawnConfigSubmit) {
  return axios.post('/bossrespawn/v1/updateConfig', data);
}

/** 删除覆盖配置，脚本随即回落自身默认值 */
export function deleteBossRespawnConfig(eventName: string) {
  return axios.post('/bossrespawn/v1/deleteConfig', { eventName });
}

/** 强制重载配置并重建排期（立即生效） */
export function reloadBossRespawnConfig() {
  return axios.post('/bossrespawn/v1/reload');
}
