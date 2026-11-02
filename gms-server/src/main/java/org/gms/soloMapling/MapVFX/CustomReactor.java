package org.gms.soloMapling.MapVFX;

import lombok.extern.slf4j.Slf4j;
import org.gms.client.Character;
import org.gms.client.inventory.Item;
import org.gms.server.maps.MapleMap;
import org.gms.server.maps.Reactor;
import org.gms.util.PacketCreator;

/**
 * 让机器人「打」反应堆，以及按需从反应堆位置掉出指定物品。
 *
 * <p><b>为什么不直接调 {@code Reactor#hitReactor(Client)}</b>：那个方法进去第一件事就是
 * {@code c.getPlayer()}，而机器人共用无头 {@code BotClient}，player 恒为 null（之前在
 * {@code MapScriptManager} 上踩过同一个坑）。这里改成推状态 + 自己广播。
 *
 * <p>行为与上游 SoloMapling 的 {@code CustomReactor.hitReactor} 一致：只做
 * {@code state++} 和 {@code triggerReactor} 广播，<b>不跑反应堆脚本、也不销毁反应堆</b>。
 * 「打碎了没有」统一用 {@code state >= 4} 判定。
 *
 * @author WorkBuddy
 * @since 2026-11-02
 */
@Slf4j
public final class CustomReactor {

    private CustomReactor() {
    }

    /**
     * 让机器人打一下指定 oid 的反应堆。
     *
     * @param stance 攻击姿态（0 = 朝右，0x80 = 朝左），只影响客户端播放的动画方向
     * @return 是否真的推进了状态
     */
    public static boolean hitReactor(MapleMap map, int oid, int stance) {
        if (map == null) {
            return false;
        }
        Reactor reactor = map.getReactorByOid(oid);
        if (reactor == null || !reactor.isAlive()) {
            return false;
        }
        return hitReactor(map, reactor, stance);
    }

    /**
     * 打一下反应堆：状态 +1，然后广播 {@code triggerReactor} 让客户端播动画。
     *
     * <p>与上游 SoloMapling 的 {@code CustomReactor.hitReactor} 行为完全一致 ——
     * <b>不销毁、不跑脚本</b>，只推状态。OPQ 的云朵 / 箱子就是「4 下打碎」，
     * 而「碎没碎」在全链路里统一以 {@code state >= 4} 判定，机器人、协调器、
     * 拾取判定都用这一个口径，所以这里绝对不能顺手把反应堆 destroy 掉：
     * 一 destroy，客户端上的云朵会直接消失（而不是播破碎动画），
     * 并且 {@code getAllReactors()} 里也查不到它了。
     */
    public static boolean hitReactor(MapleMap map, Reactor reactor, int stance) {
        if (map == null || reactor == null || !reactor.isAlive()) {
            return false;
        }
        try {
            reactor.lockReactor();
            try {
                byte state = (byte) (reactor.getState() + 1);
                reactor.setState(state);
                map.broadcastMessage(PacketCreator.triggerReactor(reactor, stance));
                return true;
            } finally {
                reactor.unlockReactor();
            }
        } catch (Exception e) {
            log.warn("[CustomReactor] 反应堆 {} 命中失败：{}", reactor.getObjectId(), e.toString());
            return false;
        }
    }

    /**
     * 在反应堆的位置掉出 {@code qty} 个 {@code itemId}。
     *
     * <p>OPQ 的云片 / 唱片本来该由反应堆掉落表给，但那张表在本项目里是按 WZ 走的、
     * 且状态推进不经过脚本，所以由调用方显式补掉 —— 和上游的做法一致。
     */
    public static boolean dropItemAtReactor(MapleMap map, Reactor reactor, int itemId, int qty, Character owner) {
        if (map == null || reactor == null || itemId <= 0 || qty <= 0) {
            return false;
        }
        try {
            Item item = new Item(itemId, (short) 0, (short) qty);
            map.spawnItemDrop(owner == null ? reactor : owner, owner, item, reactor.getPosition(), true, false);
            return true;
        } catch (Exception e) {
            log.warn("[CustomReactor] 反应堆 {} 掉物品 {} 失败：{}", reactor.getObjectId(), itemId, e.toString());
            return false;
        }
    }

    public static boolean dropItemAtReactor(MapleMap map, int oid, int itemId, int qty, Character owner) {
        return dropItemAtReactor(map, map == null ? null : map.getReactorByOid(oid), itemId, qty, owner);
    }

    /**
     * 与上游同名同签名的重载：在反应堆位置掉 1 个。
     */
    public static boolean dropItemAtReactor(MapleMap map, int oid, int itemId, Character owner) {
        return dropItemAtReactor(map, oid, itemId, 1, owner);
    }

    /**
     * 与上游同名同签名的重载：打一下（不指定朝向）。
     */
    public static boolean hitReactor(MapleMap map, int oid) {
        return hitReactor(map, oid, 0);
    }
}
