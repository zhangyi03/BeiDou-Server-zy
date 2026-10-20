package org.gms.soloMapling.ArtificialPlayer.BotMovementSystem;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.extern.slf4j.Slf4j;
import org.gms.client.Character;
import org.gms.net.packet.ByteBufInPacket;
import org.gms.net.packet.InPacket;
import org.gms.server.maps.Foothold;
import org.gms.server.maps.MapleMap;
import org.gms.util.PacketCreator;

import java.awt.Point;

/**
 * 机器人最小移动能力：手工构造 {@code MOVE_PLAYER} 的移动数据段并广播。
 *
 * <p>目前 <b>不在出生时调用</b>。原因：客户端的「图层」（角色被前景树/物件遮挡的先后顺序）
 * 是按角色坐标算的，出生点摆在空中 → 这份图层也按空中的高度算，之后再补 MOVE_PLAYER
 * 只改坐标、不会把图层算回来，画面上就会「人已落地、却还压在树后面」。
 * 所以 {@code BotGeneration} 现在直接让机器人出生在地面上，出场和「真实玩家被传送进图」
 * 完全一样（真实玩家进图时服务端也只发一个 spawn 包，没有额外动画）。
 *
 * <p>保留这个类给后面的移动系统（走路/巡逻）复用：那时每一帧都从地面坐标出发，
 * 不会再出现「空中出生」这种一次性错误。
 */
@Slf4j
public final class BotMovement {

    /** v83 客户端 stance：4/5 = 右/左站立，6/7 = 右/左跳跃 */
    public static final byte STANCE_IDLE_RIGHT = 4;
    public static final byte STANCE_IDLE_LEFT = 5;
    public static final byte STANCE_JUMP_RIGHT = 6;
    public static final byte STANCE_JUMP_LEFT = 7;

    /** move 命令类型：0 = 普通位移（带绝对坐标） */
    private static final byte MOVE_NORMAL = 0;

    /** 出生时悬在地面上方的像素数：没有这段落差，客户端看不出「掉下来」 */
    public static final int DROP_HEIGHT = 120;

    private BotMovement() {
    }

    /**
     * 把机器人从当前位置竖直落到脚下的 foothold 上，并向地图广播这段下落动画。
     *
     * <p><b>当前没有调用点</b>：出生时用它会把客户端图层算错（见类注释）。留给移动系统里
     * 「从台阶跳下 / 从传送门落下」这类本来就在空中的场景。
     *
     * @return 是否真的播了；脚下没有地面 / 本来就在地面上时返回 false
     */
    public static boolean playPortalDropDown(Character bot) {
        if (bot == null || bot.getMap() == null) {
            return false;
        }
        MapleMap map = bot.getMap();
        Point from = bot.getPosition();
        Foothold foothold = map.getFootholds().findBelow(new Point(from.x, from.y - 14));
        if (foothold == null) {
            return false;
        }

        int groundY = foothold.calculateFooting(from.x);
        if (Math.abs(groundY - from.y) < 4) {
            return false; // 已经站在地上了，没必要播
        }

        byte idleStance = isLeftFacing(bot.getStance()) ? STANCE_IDLE_LEFT : STANCE_IDLE_RIGHT;
        byte jumpStance = idleStance == STANCE_IDLE_LEFT ? STANCE_JUMP_LEFT : STANCE_JUMP_RIGHT;

        // 服务端是权威位置：先把坐标落到地面，再广播动画。
        // 顺序反过来的话，动画还没播完别人就查到 bot 还在空中。
        bot.setPosition(new Point(from.x, groundY));
        bot.setStance(idleStance);
        map.movePlayer(bot, bot.getPosition());

        ByteBuf body = buildDropBody(from.x, from.y, groundY, foothold.getId(), jumpStance, idleStance);
        int length = body.readableBytes();
        InPacket packet = new ByteBufInPacket(body);
        map.broadcastMessage(bot, PacketCreator.movePlayer(bot.getId(), packet, length), false);

        log.debug("[BotMovement] 机器人 {} 下落 {} -> {}（fh={}）", bot.getName(), from.y, groundY, foothold.getId());
        return true;
    }

    /**
     * 构造 MOVE_PLAYER 的移动数据段（不含 cid 前缀，{@code PacketCreator#movePlayer} 会补）。
     *
     * <p>结构照抄真实客户端录制的 {@code portalenterdrop}：三条命令、每条 14 字节。
     * 前两条只是把角色顶成跳跃姿势（客户端据此播放"掉下来"的动画），最后一条用
     * duration 把角色插值到地面坐标并切成站立姿势 —— 位移主要发生在第三条。
     */
    private static ByteBuf buildDropBody(int x, int fromY, int toY, int fh, byte jumpStance, byte idleStance) {
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(3);

        writeMove(buf, x, fromY, 0, jumpStance, 180, 0);
        writeMove(buf, x, fromY + 1, 0, jumpStance, 0, 5);
        writeMove(buf, x, toY, fh, idleStance, 0, 400);
        return buf;
    }

    private static void writeMove(ByteBuf buf, int x, int y, int fh, byte stance, int yWobble, int duration) {
        buf.writeByte(MOVE_NORMAL);
        buf.writeShortLE(x);
        buf.writeShortLE(y);
        buf.writeShortLE(0);        // x wobble
        buf.writeShortLE(yWobble);  // y wobble（纵轴速度）
        buf.writeShortLE(fh);       // foothold id
        buf.writeByte(stance);
        buf.writeShortLE(duration);
    }

    private static boolean isLeftFacing(int stance) {
        return stance == STANCE_IDLE_LEFT || stance == STANCE_JUMP_LEFT;
    }

    /**
     * 出生坐标：站在地面上方 {@link #DROP_HEIGHT} 像素处，配 {@link #playPortalDropDown} 播放。
     */
    public static Point dropStartPoint(Point ground) {
        return new Point(ground.x, ground.y - DROP_HEIGHT);
    }
}
