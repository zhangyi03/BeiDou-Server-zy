package org.gms.soloMapling.ArtificialPlayer;

import java.awt.Point;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.extern.slf4j.Slf4j;
import org.gms.server.maps.MapleMap;

/**
 * 机器人侧的小工具。上游对应的是同名的 {@code BotHelpers}（204 行，掺了视距、地图判定等），
 * 这里只保留行为代码真正用得到的那几个。
 *
 * @author WorkBuddy
 * @since 2026-11-02
 */
@Slf4j
public final class BotHelpers {

    private BotHelpers() {
    }

    /**
     * 阻塞当前线程。只在「链式动作」的虚拟线程里用，别在 tick 里调 —— tick 里要等就用
     * {@code BotBrain#waitFor}。
     */
    public static void blockingSleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static int random(int bound) {
        return bound <= 0 ? 0 : ThreadLocalRandom.current().nextInt(bound);
    }

    public static long randomBetween(long lo, long hi) {
        long a = Math.min(lo, hi);
        long b = Math.max(lo, hi);
        return b > a ? ThreadLocalRandom.current().nextLong(a, b + 1) : a;
    }

    public static int randomBetween(int lo, int hi) {
        int a = Math.min(lo, hi);
        int b = Math.max(lo, hi);
        return b > a ? ThreadLocalRandom.current().nextInt(a, b + 1) : a;
    }

    public static <T> T randomElement(List<T> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(ThreadLocalRandom.current().nextInt(list.size()));
    }

    public static boolean isPointNear(Point a, Point b, int tolerance) {
        return a != null && b != null
                && Math.abs(a.x - b.x) <= tolerance
                && Math.abs(a.y - b.y) <= tolerance;
    }

    /**
     * 地图上有没有这个 NPC 的实体。任务/组队任务里常用来判断「这一步该不该做」。
     */
    public static boolean isNpcPresent(MapleMap map, int npcId) {
        return map != null && map.getNPCById(npcId) != null;
    }

    /**
     * 两个坐标之间的水平距离。
     */
    public static int dx(Point a, Point b) {
        return a == null || b == null ? Integer.MAX_VALUE : Math.abs(a.x - b.x);
    }

    /**
     * 两个坐标之间的直线距离。
     */
    public static double distance(Point a, Point b) {
        return a == null || b == null ? Double.MAX_VALUE : a.distance(b);
    }

    /**
     * 这个角色是不是机器人（用 {@link org.gms.soloMapling.BotRegistry} 登记表判定，
     * 比上游的「id &gt; 20000」更准）。
     */
    public static boolean isBot(org.gms.client.Character chr) {
        return chr != null && org.gms.soloMapling.BotRegistry.isBot(chr.getId());
    }

    /**
     * 按两个时间戳的差值阻塞等待（上游原样保留：录制回放时按包时间轴推进）。
     *
     * @return false 表示等待被中断
     */
    public static boolean waitBetweenTwoLong(long timestamp1, long timestamp2) {
        long diff = Math.max(0, timestamp2 - timestamp1);
        if (diff <= 0) {
            return true;
        }
        try {
            Thread.sleep(diff);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
