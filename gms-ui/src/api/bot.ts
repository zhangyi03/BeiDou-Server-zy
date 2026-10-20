import axios from 'axios';

/** 机器人列表项：名字/等级/职业来自 characters 表，online 是运行时状态 */
export interface BotProfileResult {
  id: number;
  characterId: number;
  accountId: number;
  name: string;
  level: number;
  job: number;
  gender: number;
  mapId: number;
  spawnX: number;
  spawnY: number;
  autoStart: boolean;
  botType: string;
  note: string;
  online: boolean;
  /** 外观与装备是否已生成并落库 */
  decorated: boolean;
  /** 人设档 S/A/B/C/D */
  botTier: string;
  createTime: string | null;
  updateTime: string | null;
}

export interface BotPageState {
  records: BotProfileResult[];
  pageNumber: number;
  pageSize: number;
  totalPage: number;
  totalRow: number;
}

export interface BotCreateForm {
  name?: string;
  level?: number;
  job?: number;
  gender?: number;
  mapId?: number;
  spawnX?: number;
  spawnY?: number;
  autoStart?: boolean;
  /** 行为类型码，见 src/views/bot/botTypes.ts；留空 = IDLE */
  botType?: string;
  note?: string;
}

export interface BotUpdateForm {
  id: number;
  level?: number;
  job?: number;
  mapId?: number;
  spawnX?: number;
  spawnY?: number;
  autoStart?: boolean;
  botType?: string;
  note?: string;
}

export function getBotList(page: number, size: number, name?: string) {
  let url = `/bot/v1/list?page=${page}&size=${size}`;
  if (name) url += `&name=${encodeURIComponent(name)}`;
  return axios.get<BotPageState>(url);
}

export function createBot(data: BotCreateForm) {
  return axios.post('/bot/v1/create', data);
}

export function updateBot(data: BotUpdateForm) {
  return axios.post('/bot/v1/update', data);
}

export function spawnBots(ids: number[]) {
  return axios.post('/bot/v1/spawn', { ids });
}

export function despawnBots(ids: number[]) {
  return axios.post('/bot/v1/despawn', { ids });
}

export function deleteBots(ids: number[]) {
  return axios.post('/bot/v1/delete', { ids });
}

/** 重置装备：只换身上那套装备，发型/脸/肤色保持不变 */
export function resetBotEquip(ids: number[]) {
  return axios.post('/bot/v1/resetEquip', { ids });
}

// ------------------------------------------------------------------

export type BotPoolType = 'GENERIC' | 'NX' | 'OMIT' | 'SELL';

export interface BotEquipGenericRow {
  id?: number;
  category: string;
  itemId: number;
  enabled: boolean;
}

export interface BotEquipNxRow {
  id?: number;
  category: string;
  itemId: number;
  /** 空串=按物品 ID 第四位推导性别 */
  gender: string;
  enabled: boolean;
}

export interface BotEquipOmitRow {
  id?: number;
  blockType: string;
  itemId: number;
  rangeStart: number;
  rangeEnd: number;
  note: string;
  enabled: boolean;
}

export function getGenericPool(category?: string) {
  const url = category
    ? `/bot/v1/equipPool/generic?category=${category}`
    : '/bot/v1/equipPool/generic';
  return axios.get<BotEquipGenericRow[]>(url);
}

export function getNxPool(category?: string) {
  const url = category
    ? `/bot/v1/equipPool/nx?category=${category}`
    : '/bot/v1/equipPool/nx';
  return axios.get<BotEquipNxRow[]>(url);
}

export function getOmitPool(blockType?: string) {
  const url = blockType
    ? `/bot/v1/equipPool/omit?blockType=${blockType}`
    : '/bot/v1/equipPool/omit';
  return axios.get<BotEquipOmitRow[]>(url);
}

export function saveGenericPool(data: BotEquipGenericRow) {
  return axios.post('/bot/v1/equipPool/generic/save', data);
}

export function saveNxPool(data: BotEquipNxRow) {
  return axios.post('/bot/v1/equipPool/nx/save', data);
}

export function saveOmitPool(data: BotEquipOmitRow) {
  return axios.post('/bot/v1/equipPool/omit/save', data);
}

export function deletePoolRows(poolType: BotPoolType, ids: number[]) {
  return axios.post('/bot/v1/equipPool/delete', { poolType, ids });
}

/** 无请求体，别给它传 data（拦截器不会包装空 body） */
export function reloadEquipPools() {
  return axios.post('/bot/v1/equipPool/reload');
}

// ------------------------------------------------------------------ 摆摊出售物品池

/** 一格货：买家付 price * 组数，拿到 item 数量 * 组数 个 */
export interface BotSellPoolRow {
  id?: number;
  /** scroll / darkscroll / star / potion */
  category: string;
  itemId: number;
  /** 每格单价（mesos） */
  price: number;
  /** 每格数量下限 */
  qtyMin: number;
  /** 每格数量上限 */
  qtyMax: number;
  /** 抽签权重，越大越容易被摆出来 */
  weight: number;
  enabled: boolean;
  note: string;
}

export function getSellPool(category?: string) {
  const url = category
    ? `/bot/v1/sellPool?category=${category}`
    : '/bot/v1/sellPool';
  return axios.get<BotSellPoolRow[]>(url);
}

export function saveSellPool(data: BotSellPoolRow) {
  return axios.post('/bot/v1/sellPool/save', data);
}

/** 无请求体，别给它传 data */
export function reloadSellPool() {
  return axios.post('/bot/v1/sellPool/reload');
}
