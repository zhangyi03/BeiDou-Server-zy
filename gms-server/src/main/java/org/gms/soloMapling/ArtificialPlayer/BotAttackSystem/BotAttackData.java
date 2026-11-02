package org.gms.soloMapling.ArtificialPlayer.BotAttackSystem;

import org.gms.client.inventory.WeaponType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 机器人挥击动画用的「body action id」表。
 *
 * <p>为什么需要它：攻击包里的 {@code direction} 字节<b>不是朝向</b>，而是客户端
 * {@code Character/00002000.img} 里的动作编号（{@code swingO1=5}、{@code swingP1=13} …）。
 * 传 0 会被客户端当成「站立」，于是别的玩家只看到机器人站着、看不到任何挥击动作
 * —— 这就是「机器人没有攻击云的动作」的直接原因。
 *
 * <p>第二个坑：动作编号必须和当前装备的武器类别匹配。单手挥砍的 {@code swingO1}
 * 用在长枪上，客户端同样什么都不渲染。{@link #randomActionFor(WeaponType)} 按武器
 * 类别挑一组合法的变体。
 *
 * <p>取值来自上游 SoloMapling 的 {@code BotAttackData}（其注释标注来源为 GreenCatMS 的
 * {@code BODY_ACTION_ID_OVERRIDES}，已在实机验证）。
 *
 * @author WorkBuddy
 * @since 2026-11-03
 */
public final class BotAttackData {

    private BotAttackData() {
    }

    /** 攻击包 stance 字节的朝向位。 */
    public static final int FACING_RIGHT_MASK = 0x00;

    public static final int FACING_LEFT_MASK = 0x80;

    /** 未知武器时的默认攻速，v83 取值 2-9（越小越快）。 */
    public static final int DEFAULT_ATTACK_SPEED = 4;

    // ---- body action ids ----------------------------------------------------
    private static final int SWING_O1 = 5, SWING_O2 = 6, SWING_O3 = 7;   // 单手挥砍
    private static final int SWING_T1 = 9, SWING_T2 = 10, SWING_T3 = 11; // 双手挥砍
    private static final int SWING_P1 = 13, STAB_T1 = 19;                // 长枪/长矛
    private static final int STAB_O1 = 16, STAB_O2 = 17;                 // 单手突刺
    private static final int SHOOT_1 = 22, SHOOT_2 = 23;                 // 弓 / 弩
    private static final int CLAW_1 = 24, CLAW_2 = 25, CLAW_3 = 26;      // 拳套投掷
    private static final int WAND_1 = 28, WAND_2 = 29;                   // 短杖 / 长杖

    // ---- 按武器类别分组的动作变体 ---------------------------------------------
    private static final int[] DEFAULT_1H_VARIANTS = {STAB_O1, STAB_O2, SWING_O1, SWING_O2, SWING_O3};
    private static final int[] HEAVY_2H_VARIANTS = {STAB_O1, STAB_O2, SWING_T1, SWING_T2, SWING_T3};
    private static final int[] POLEARM_VARIANTS = {SWING_P1, STAB_T1};
    private static final int[] WAND_VARIANTS = {WAND_1, WAND_2};
    private static final int[] CLAW_VARIANTS = {CLAW_1, CLAW_2, CLAW_3};
    // 弓和弩不通用：shoot1 是弓的拉弓动作，shoot2 是弩。混用会有一半概率动作错位。
    private static final int[] BOW_VARIANTS = {SHOOT_1};
    private static final int[] CROSSBOW_VARIANTS = {SHOOT_2};

    /**
     * 按武器类别随机取一个可渲染的动作编号。
     *
     * @param weaponType 当前主手武器类别，{@code null} 表示空手，走单手默认组
     */
    public static int randomActionFor(WeaponType weaponType) {
        int[] variants = variantsFor(weaponType);
        return variants[ThreadLocalRandom.current().nextInt(variants.length)];
    }

    private static int[] variantsFor(WeaponType weaponType) {
        if (weaponType == null) {
            return DEFAULT_1H_VARIANTS;
        }
        return switch (weaponType) {
            case SWORD2H, GENERAL2H_SWING, GENERAL2H_STAB -> HEAVY_2H_VARIANTS;
            case SPEAR_SWING, SPEAR_STAB, POLE_ARM_SWING, POLE_ARM_STAB -> POLEARM_VARIANTS;
            case WAND, STAFF -> WAND_VARIANTS;
            case CLAW -> CLAW_VARIANTS;
            case BOW -> BOW_VARIANTS;
            case CROSSBOW -> CROSSBOW_VARIANTS;
            // 单手剑/匕首/指虎/枪 等：都吃单手挥砍组
            default -> DEFAULT_1H_VARIANTS;
        };
    }
}
