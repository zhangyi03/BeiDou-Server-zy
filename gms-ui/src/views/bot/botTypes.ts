/**
 * 机器人行为类型登记表（前端侧）。
 *
 * <p>value 是落库的类型码，必须与后端
 * {@code org.gms.soloMapling.ArtificialPlayer.BotType} 的枚举名逐字一致（已落库，不要改名）；
 * 中文/英文文案走 i18n，key 见 {@code src/views/bot/list/locale/*}。
 *
 * <p>注意：类型目前只是档案字段，行为逻辑（打怪 / 摆摊 / 跟随等）还没移植，
 * 上线行为与类型无关，一律走通用生成 + 装饰链路。
 */
export interface BotTypeOption {
  /** 落库类型码，与后端枚举名一致 */
  value: string;
  /** 界面文案的 i18n key */
  labelKey: string;
}

export const BOT_TYPE_OPTIONS: BotTypeOption[] = [
  { value: 'IDLE', labelKey: 'bot.type.idle' },
  { value: 'DICE_BOT', labelKey: 'bot.type.dice' },
  { value: 'TUTORIAL_BOT', labelKey: 'bot.type.tutorial' },
  { value: 'FM_BOT', labelKey: 'bot.type.fm' },
  { value: 'SCROLL_BOT', labelKey: 'bot.type.scroll' },
  { value: 'SELLING_MERCHANT_BOT', labelKey: 'bot.type.sellingMerchant' },
  { value: 'BUYING_MERCHANT_BOT', labelKey: 'bot.type.buyingMerchant' },
  { value: 'NX_MERCHANT_BOT', labelKey: 'bot.type.nxMerchant' },
  { value: 'GACHA_BOT', labelKey: 'bot.type.gacha' },
  { value: 'HENESYS_BOT', labelKey: 'bot.type.henesys' },
  { value: 'HENESYS_JQ_BOT', labelKey: 'bot.type.henesysJq' },
  { value: 'GAME_ZONE_HOST_BOT', labelKey: 'bot.type.gameZoneHost' },
  { value: 'BLACKJACK_DEALER', labelKey: 'bot.type.blackjack' },
  { value: 'DROP_GAME_BOT', labelKey: 'bot.type.dropGame' },
  { value: 'OPQ_BOT', labelKey: 'bot.type.opq' },
  { value: 'SOCIAL_BOT', labelKey: 'bot.type.social' },
  { value: 'TOWN_WANDERER_BOT', labelKey: 'bot.type.townWanderer' },
  { value: 'TEST_ATTACK_BOT', labelKey: 'bot.type.testAttack' },
  { value: 'TRAINING_BOT', labelKey: 'bot.type.training' },
  { value: 'FOLLOWER_BOT', labelKey: 'bot.type.follower' },
];

/** 新建机器人时的默认类型 */
export const BOT_TYPE_DEFAULT = 'IDLE';

/** 类型码 → i18n key；未知码（老数据）返回 undefined，由调用方回退显示原始码 */
export function findBotTypeKey(code?: string | null): string | undefined {
  if (!code) return undefined;
  return BOT_TYPE_OPTIONS.find((o) => o.value === code)?.labelKey;
}
