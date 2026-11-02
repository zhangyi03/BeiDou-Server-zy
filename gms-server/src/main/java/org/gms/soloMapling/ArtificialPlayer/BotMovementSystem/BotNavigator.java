package org.gms.soloMapling.ArtificialPlayer.BotMovementSystem;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.gms.client.Character;
import org.gms.net.packet.ByteBufInPacket;
import org.gms.net.packet.Packet;
import org.gms.server.maps.Foothold;
import org.gms.server.maps.MapleMap;
import org.gms.server.maps.Portal;
import org.gms.util.PacketCreator;

/**
 * 机器人走位：每帧走一小步，必要时跳一段抛物线。
 *
 * <p><b>为什么不用上游那一套</b>：上游的 {@code MovementCommands + NavigationSystem} 是
 * 「录制回放」架构 —— 靠 {@code movementDataPackets/map<id>/c1-2a.bin} 这类人工录下来的
 * 移动包序列，再叠一张 jgrapht 的连通图做寻路，整套 4000 行左右，还额外依赖 jgrapht。
 * 那套东西和上游那份 fork 的封包实现绑得很死，搬到本项目里风险远大于收益。
 *
 * <p>这里改成「服务端权威 + 客户端插值」的简化实现，只做三件事：
 * <ol>
 *   <li><b>平地走</b>：每帧朝目标走 {@value #WALK_STEP_PX}px，y 由脚下 foothold 重新算，
 *       所以上下坡、走下台阶都会自然跟着地面走；</li>
 *   <li><b>跳上去 / 跳下去</b>：目标和自己不在同一层（y 差超过 {@value #LAYER_TOLERANCE_PX}）
 *       且水平距离在 {@value #JUMP_MAX_DX_PX}px 以内时，播一段抛物线，直接把角色插值到落点；</li>
 *   <li><b>转身</b>：只换 stance，不移动。</li>
 * </ol>
 *
 * <p>调用方式：状态机里每帧调一次 {@link #walkTo}，返回 true 表示到了。不要自己循环，
 * 也不要 sleep —— 走位是跨帧推进的。
 *
 * @author WorkBuddy
 * @since 2026-11-02
 */
@Slf4j
public final class BotNavigator {

    /**
     * 每帧最多走多远。
     *
     * <p>取值要和 tick 周期、{@link #STEP_DURATION_MS} 一起看：走一步的动画时长必须
     * <b>略短于</b> tick 周期，客户端才会从这一步的末尾直接接上下一步，看起来是连续行走；
     * 反过来（动画比周期长）会每步都被下一条移动包打断，看起来就是「瞬移 + 卡顿」。
     * 现在的组合是 250ms 一帧、每步 55px、动画 240ms，约合 220px/s，和真人步行接近。
     */
    public static final int WALK_STEP_PX = 55;

    /**
     * 认为「已经到站」的水平容差。
     */
    public static final int ARRIVE_TOLERANCE_PX = 30;

    /**
     * y 差超过这个值就算不同层，需要跳。
     */
    public static final int LAYER_TOLERANCE_PX = 60;

    /**
     * 超过这个水平距离的高差目标，先走过去再跳。
     */
    public static final int JUMP_MAX_DX_PX = 260;

    /**
     * 一段移动命令的时长（毫秒）。客户端按它插值：太长会和下一帧的移动包冲突（一顿一顿），
     * 太短则每步都像瞬移。取 240ms 略小于 250ms 的 tick，衔接最顺。
     */
    private static final int STEP_DURATION_MS = 240;

    /**
     * 抛物线分几段。段数越多越平滑，包也越大。
     */
    private static final int JUMP_SEGMENTS = 8;

    private BotNavigator() {
    }

    /**
     * 朝目标走一步（可跨层）。
     *
     * @return 已经到达（含「目标就在脚下」的情况）
     */
    public static boolean walkTo(Character bot, Point target) {
        return walkTo(bot, target, true);
    }

    /**
     * 朝目标走一步。
     *
     * @param allowJump false 时只做平地走（目标在高处会一直走不到，适合「只在地面巡逻」）
     * @return 已经到达
     */
    public static boolean walkTo(Character bot, Point target, boolean allowJump) {
        if (bot == null || target == null || bot.getMap() == null) {
            return false;
        }
        Point from = bot.getPosition();
        int dx = target.x - from.x;
        int dy = target.y - from.y;

        if (Math.abs(dx) <= ARRIVE_TOLERANCE_PX && Math.abs(dy) <= LAYER_TOLERANCE_PX) {
            faceTowards(bot, dx);
            return true;
        }

        MapleMap map = bot.getMap();

        // 目标明显在另一层：先横向对齐，再跳
        if (allowJump && Math.abs(dy) > LAYER_TOLERANCE_PX && Math.abs(dx) <= JUMP_MAX_DX_PX) {
            if (Math.abs(dx) > ARRIVE_TOLERANCE_PX) {
                stepHorizontally(bot, map, from, dx, Math.min(Math.abs(dx), WALK_STEP_PX));
                return false;
            }
            jumpTo(bot, map, target);
            return true;
        }

        // 平地（或同一层内的斜坡）：按 foothold 算新 y
        Point next = walkablePoint(map, new Point(from.x + clamp(dx, WALK_STEP_PX), from.y));
        if (next == null) {
            // 前方没有地面（走到断崖/地图边缘）：原地停住，交给调用方换策略
            idle(bot);
            return false;
        }
        stepHorizontally(bot, map, from, dx, Math.abs(next.x - from.x));
        return false;
    }

    /**
     * 走到某个传送门并站在门口。
     *
     * @return 已经站在门口
     */
    public static boolean moveToPortal(Character bot, int portalId) {
        if (bot == null || bot.getMap() == null) {
            return false;
        }
        Portal portal = bot.getMap().getPortal(portalId);
        if (portal == null) {
            log.warn("[BotNavigator] 地图 {} 上没有传送门 {}，机器人 {} 无法前往",
                    bot.getMapId(), portalId, bot.getName());
            return false;
        }
        return walkTo(bot, portal.getPosition());
    }

    /**
     * 让机器人站定（播一个站姿，避免一直保持着走路姿势）。
     */
    public static void idle(Character bot) {
        if (bot == null || bot.getMap() == null) {
            return;
        }
        byte stance = isFacingLeft(bot.getStance()) ? BotMovement.STANCE_IDLE_LEFT : BotMovement.STANCE_IDLE_RIGHT;
        if (bot.getStance() == stance) {
            return;
        }
        bot.setStance(stance);
        bot.getMap().broadcastMessage(movePacket(bot.getId(), buildIdleBody(bot.getPosition(), stance)));
    }

    /**
     * 朝向某个点（只翻面，不移动）。
     */
    public static void facePoint(Character bot, Point target) {
        if (bot == null || target == null) {
            return;
        }
        faceTowards(bot, target.x - bot.getPosition().x);
    }

    public static boolean isFacingLeft(int stance) {
        return stance == BotMovement.STANCE_IDLE_LEFT
                || stance == BotMovement.STANCE_JUMP_LEFT
                || stance == BotMovement.STANCE_MOVE_LEFT;
    }

    /**
     * 原地小幅转身（先往反方向挪 3px 再回来），用来表现「犹豫一下」。
     */
    public static void microTurnAround(Character bot, boolean toLeft) {
        if (bot == null || bot.getMap() == null) {
            return;
        }
        Point from = bot.getPosition();
        int dx = toLeft ? -3 : 3;
        Point next = walkablePoint(bot.getMap(), new Point(from.x + dx, from.y));
        Point landing = next == null ? from : next;
        byte stance = toLeft ? BotMovement.STANCE_MOVE_LEFT : BotMovement.STANCE_MOVE_RIGHT;
        bot.setPosition(landing);
        bot.setStance(stance);
        bot.getMap().movePlayer(bot, landing);
        bot.getMap().broadcastMessage(movePacket(bot.getId(),
                buildSingleMoveBody(landing, 0, stance, 200)));
        bot.setStance(toLeft ? BotMovement.STANCE_IDLE_LEFT : BotMovement.STANCE_IDLE_RIGHT);
    }

    // ------------------------------------------------------------------ 内部

    private static void stepHorizontally(Character bot, MapleMap map, Point from, int dx, int stepPx) {
        int dir = dx >= 0 ? 1 : -1;
        int newX = from.x + dir * Math.max(1, stepPx);
        Point next = walkablePoint(map, new Point(newX, from.y));
        if (next == null) {
            idle(bot);
            return;
        }
        byte stance = dir > 0 ? BotMovement.STANCE_MOVE_RIGHT : BotMovement.STANCE_MOVE_LEFT;
        bot.setPosition(next);
        bot.setStance(stance);
        map.movePlayer(bot, next);
        map.broadcastMessage(movePacket(bot.getId(),
                buildSingleMoveBody(next, footholdIdAt(map, next), stance, STEP_DURATION_MS)));
    }

    /**
     * 抛物线跳跃：把角色沿 8 段插值到落点，客户端看着就是跳过去。
     */
    private static void jumpTo(Character bot, MapleMap map, Point target) {
        Point from = bot.getPosition();
        Point landing = walkablePoint(map, target);
        if (landing == null) {
            landing = target;
        }
        boolean toLeft = landing.x < from.x;
        byte jumpStance = toLeft ? BotMovement.STANCE_JUMP_LEFT : BotMovement.STANCE_JUMP_RIGHT;
        byte idleStance = toLeft ? BotMovement.STANCE_IDLE_LEFT : BotMovement.STANCE_IDLE_RIGHT;

        List<Point> arc = new ArrayList<>(JUMP_SEGMENTS);
        int peak = Math.min(from.y, landing.y) - 80; // 起跳抬升的高度
        for (int i = 1; i <= JUMP_SEGMENTS; i++) {
            double t = (double) i / JUMP_SEGMENTS;
            int x = (int) Math.round(from.x + (landing.x - from.x) * t);
            // 二次曲线：起跳 / 落地时贴合两端高度，中间抬到 peak
            double h = 4 * t * (1 - t);
            int baseY = (int) Math.round(from.y + (landing.y - from.y) * t);
            int y = (int) Math.round(baseY + (peak - baseY) * h);
            arc.add(new Point(x, y));
        }

        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(arc.size());
        for (int i = 0; i < arc.size(); i++) {
            Point p = arc.get(i);
            boolean last = i == arc.size() - 1;
            writeMove(buf, p.x, p.y, last ? footholdIdAt(map, p) : 0,
                    last ? idleStance : jumpStance, 0, 80);
        }
        bot.setPosition(landing);
        bot.setStance(idleStance);
        map.movePlayer(bot, landing);
        map.broadcastMessage(bot, PacketCreator.movePlayer(bot.getId(), new ByteBufInPacket(buf), buf.readableBytes()), false);
    }

    private static void faceTowards(Character bot, int dx) {
        if (dx == 0 || bot.getMap() == null) {
            return;
        }
        byte wanted = dx > 0 ? BotMovement.STANCE_IDLE_RIGHT : BotMovement.STANCE_IDLE_LEFT;
        if (bot.getStance() == wanted) {
            return;
        }
        bot.setStance(wanted);
        bot.getMap().broadcastMessage(movePacket(bot.getId(), buildIdleBody(bot.getPosition(), wanted)));
    }

    /**
     * 给定 x，把 y 吸到脚下那块地面上；该列没有地面返回 null。
     */
    private static Point walkablePoint(MapleMap map, Point from) {
        Foothold foothold = map.getFootholds().findBelow(new Point(from.x, from.y - 14));
        if (foothold == null) {
            return null;
        }
        return new Point(from.x, foothold.calculateFooting(from.x) - 1);
    }

    private static int footholdIdAt(MapleMap map, Point p) {
        Foothold foothold = map.getFootholds().findBelow(new Point(p.x, p.y - 14));
        return foothold == null ? 0 : foothold.getId();
    }

    private static int clamp(int value, int limit) {
        return Math.max(-limit, Math.min(limit, value));
    }

    private static ByteBuf buildSingleMoveBody(Point to, int footholdId, byte stance, int duration) {
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(1);
        writeMove(buf, to.x, to.y, footholdId, stance, 0, duration);
        return buf;
    }

    private static ByteBuf buildIdleBody(Point at, byte stance) {
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(1);
        writeMove(buf, at.x, at.y, 0, stance, 0, 0);
        return buf;
    }

    private static void writeMove(ByteBuf buf, int x, int y, int fh, byte stance, int yWobble, int duration) {
        buf.writeByte(0);            // 0 = 普通位移
        buf.writeShortLE(x);
        buf.writeShortLE(y);
        buf.writeShortLE(0);         // x wobble
        buf.writeShortLE(yWobble);   // y wobble
        buf.writeShortLE(fh);        // foothold id
        buf.writeByte(stance);
        buf.writeShortLE(duration);
    }

    /**
     * 把移动数据段包成 MOVE_PLAYER 封包（cid 前缀由 PacketCreator 补）。
     */
    private static Packet movePacket(int cid, ByteBuf body) {
        int length = body.readableBytes();
        return PacketCreator.movePlayer(cid, new ByteBufInPacket(body), length);
    }
}
