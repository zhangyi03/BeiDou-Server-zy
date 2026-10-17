package org.gms.soloMapling.server;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 机器人侧的随机数工具。
 *
 * <p>上游这个类同时持有 {@code channel} / {@code world} 两个静态字段（在类加载期就
 * 去 {@code Server.getInstance()} 取频道），本轮不搬那两个字段 —— 频道/世界的获取放到
 * {@link org.gms.soloMapling.ArtificialPlayer.BotGeneration} 里按需取，避免类加载顺序
 * 在 Spring 启动早期踩到 Server 还没初始化的窗口。
 */
public class SoloMaplingUtilities {

    public static final Random random = new Random();

    public static boolean chance(double percent) {
        return random.nextDouble() * 100 < percent;
    }

    public static boolean rollChanceInverse(int outOf) {
        return random.nextInt(outOf) == 0;
    }

    public static int getRandomNumber(int x, int y) {
        return generateRandomNumber(x, y);
    }

    public static int generateRandomNumber(int x, int y) {
        if (x > y) {
            throw new IllegalArgumentException("x must be less than or equal to y.");
        }
        return ThreadLocalRandom.current().nextInt(x, y + 1);
    }

    public static int getRandomNumber(List<Integer> numbers) {
        if (numbers == null || numbers.isEmpty()) {
            throw new IllegalArgumentException("numbers must not be empty");
        }
        return numbers.get(random.nextInt(numbers.size()));
    }

    public static <T> T getRandomElement(List<T> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(random.nextInt(list.size()));
    }
}
