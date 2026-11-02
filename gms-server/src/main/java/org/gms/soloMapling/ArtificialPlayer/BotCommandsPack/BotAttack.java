package org.gms.soloMapling.ArtificialPlayer.BotCommandsPack;

import java.util.Collections;
import org.gms.client.Character;
import org.gms.client.inventory.Inventory;
import org.gms.client.inventory.InventoryType;
import org.gms.client.inventory.Item;
import org.gms.client.inventory.WeaponType;
import org.gms.server.ItemInformationProvider;
import org.gms.soloMapling.ArtificialPlayer.BotAttackSystem.BotAttackData;
import org.gms.soloMapling.ArtificialPlayer.BotMovementSystem.BotMovement;
import org.gms.util.PacketCreator;

/**
 * 机器人的「挥空」动作：只播一段攻击动画，不结算伤害。
 *
 * <p>为什么需要它：机器人打反应堆（OPQ 的云朵 / 箱子）不是真的走伤害流程 —— 伤害由服务端
 * 直接调反应堆的命中逻辑，客户端只需要看到「这个人在挥武器」。所以这里发一个
 * {@code CLOSE_RANGE_ATTACK} 且目标列表为空，地图里所有人都会看到一次挥击动作。
 *
 * <p>两个字节别搞混：
 * <ul>
 *   <li>{@code stance} = 朝向掩码（{@code 0x00} 朝右、{@code 0x80} 朝左）；</li>
 *   <li>{@code direction} = <b>body action id</b>（{@code swingO1=5}、{@code swingP1=13} …），
 *       必须和当前装备的武器类别匹配。以前这里传的是 {@code 0}，客户端当成「站立」，
 *       于是别人只看到机器人杵在那儿、没有任何攻击动作。</li>
 * </ul>
 *
 * @author WorkBuddy
 * @since 2026-11-02
 */
public final class BotAttack {

    /** 主手武器在装备栏里的槽位。 */
    private static final short EQUIP_SLOT_WEAPON = -11;

    private BotAttack() {
    }

    /**
     * 挥一下（无目标）。调用频率由调用方控制，别每帧都调。
     */
    public static void basicSwing(Character bot) {
        if (bot == null || bot.getMap() == null) {
            return;
        }
        int facingMask = facingLeft(bot)
                ? BotAttackData.FACING_LEFT_MASK
                : BotAttackData.FACING_RIGHT_MASK;
        int bodyActionId = BotAttackData.randomActionFor(resolveEquippedWeaponType(bot));
        bot.getMap().broadcastMessage(
                bot,
                PacketCreator.closeRangeAttack(
                        bot,
                        0,                       // skill
                        0,                       // skillLevel
                        facingMask,              // stance：朝向
                        0,                       // numAttackedAndDamage：没有目标
                        Collections.emptyMap(),  // 伤害表为空
                        BotAttackData.DEFAULT_ATTACK_SPEED,
                        bodyActionId,            // direction：真正的挥击动作编号
                        0),                      // display
                false);
    }

    public static boolean facingLeft(Character bot) {
        int stance = bot.getStance();
        return stance == BotMovement.STANCE_IDLE_LEFT
                || stance == BotMovement.STANCE_MOVE_LEFT
                || stance == BotMovement.STANCE_JUMP_LEFT;
    }

    /**
     * 机器人当前主手武器类别；空手或读不到时返回 {@code null}（调用方会走单手默认动作组）。
     */
    public static WeaponType resolveEquippedWeaponType(Character bot) {
        if (bot == null) {
            return null;
        }
        Inventory equipped = bot.getInventory(InventoryType.EQUIPPED);
        if (equipped == null) {
            return null;
        }
        Item weapon = equipped.getItem(EQUIP_SLOT_WEAPON);
        if (weapon == null) {
            return null;
        }
        return ItemInformationProvider.getInstance().getWeaponType(weapon.getItemId());
    }
}
