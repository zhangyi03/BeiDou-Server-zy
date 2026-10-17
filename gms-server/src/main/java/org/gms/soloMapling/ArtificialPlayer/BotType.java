package org.gms.soloMapling.ArtificialPlayer;

/**
 * 机器人行为类型登记表。
 *
 * <p>类型码与上游 SoloMapling 的 {@code BotTypeManager.BotType} 一一对应（大写枚举名即
 * 落库值 {@code bot_profile.bot_type}），便于以后把行为逻辑（打怪、摆摊、跟随便、小游戏等）
 * 逐个搬过来时直接按码分派；{@link #IDLE} 是本移植版新增的默认值，表示「站桩、什么都不做」。
 *
 * <p>本轮只做<b>登记 + 落库 + 界面展示</b>，行为实现还没移植：类型目前不影响上线行为，
 * 上线一律走 {@code BotService} 的通用生成/装饰链路。等某一类的逻辑接进来之后，
 * 在这里加 {@code of(code)} 的分派即可，类型码不要改名（已落库）。
 *
 * @author WorkBuddy
 * @since 2026-10-17
 */
public enum BotType {

    /** 站桩待机（默认） */
    IDLE("待机"),
    DICE_BOT("骰子赌局"),
    TUTORIAL_BOT("新手教学"),
    FM_BOT("自由市场挂机"),
    SCROLL_BOT("卷轴强化"),
    SELLING_MERCHANT_BOT("摆摊出售"),
    BUYING_MERCHANT_BOT("摆摊收购"),
    NX_MERCHANT_BOT("点装商人"),
    GACHA_BOT("扭蛋玩法"),
    HENESYS_BOT("射手村闲逛"),
    HENESYS_JQ_BOT("射手村跳跃任务"),
    GAME_ZONE_HOST_BOT("游戏区主持"),
    BLACKJACK_DEALER("21点庄家"),
    DROP_GAME_BOT("掉落小游戏"),
    OPQ_BOT("组队任务"),
    SOCIAL_BOT("社交聊天"),
    TOWN_WANDERER_BOT("城镇闲逛"),
    TEST_ATTACK_BOT("攻击测试"),
    TRAINING_BOT("打怪练级"),
    FOLLOWER_BOT("跟随玩家");

    /**
     * 新建机器人时未指定类型的缺省值
     */
    public static final BotType DEFAULT = IDLE;

    /**
     * 中文说明，只用于服务端日志；界面文案以前端 i18n 为准
     */
    private final String description;

    BotType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 类型码是否合法（忽略首尾空白）。
     */
    public static boolean isValid(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String trimmed = code.trim();
        for (BotType type : values()) {
            if (type.name().equals(trimmed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 空值回落 {@link #DEFAULT}，其余原样返回（调用方负责先做 {@link #isValid} 校验）。
     */
    public static String normalize(String code) {
        return code == null || code.isBlank() ? DEFAULT.name() : code.trim();
    }
}
