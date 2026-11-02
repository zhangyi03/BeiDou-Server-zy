package org.gms.soloMapling.server;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import lombok.extern.slf4j.Slf4j;

/**
 * 机器人「等一会儿再做」的统一入口：延迟一次、或按节拍串一段动作。
 *
 * <p>上游 SoloMapling 把这三个工具写得很清楚，本移植版保留同样的语义，只换掉底层线程池
 * （上游用的 {@code ExecutorServiceManager} 本项目没有）：
 *
 * <ol>
 *   <li><b>整段状态机暂停</b> —— 用 {@code BotBrain#waitFor(ms)}，不走这里。
 *       适合「卷完这张卷，接下来 10 秒什么都不做」：让出 tick，时间到了再回来。</li>
 *   <li><b>延迟一件事，但机器人继续 tick</b> —— {@link #after} / {@link #afterRandom}。
 *       定时器挂在共享调度线程上，动作体跑在虚拟线程里，慢动作不会堵住定时器。</li>
 *   <li><b>串好的节拍</b> —— {@link #chain()}，例如「喊一句 → 停 800ms → 挥手 → 再停」。
 *       整条链跑在一个虚拟线程上，{@code pause} 只挂起那条虚拟线程（几百字节），
 *       不占 OS 线程。</li>
 * </ol>
 *
 * <p><b>规则</b>：状态机 tick 里不要写 {@code Thread.sleep}，要等待就用上面三个之一。
 *
 * @author WorkBuddy
 * @since 2026-11-02
 */
@Slf4j
public final class BotTiming {

    /**
     * 定时器池。单线程足够：每个任务只是把动作丢给虚拟线程，本身不做重活。
     */
    private static final ScheduledExecutorService TIMER;

    static {
        ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "bot-timing");
            t.setDaemon(true);
            return t;
        });
        // 机器人下线后残留的延迟任务直接丢弃，不报错
        pool.setRemoveOnCancelPolicy(true);
        TIMER = pool;
    }

    private BotTiming() {
    }

    /**
     * 延迟 {@code delayMs} 后执行一次。动作体在虚拟线程里跑，异常只记日志。
     */
    public static void after(long delayMs, Runnable action) {
        if (action == null) {
            return;
        }
        TIMER.schedule(() -> runVirtual(action), Math.max(0, delayMs), TimeUnit.MILLISECONDS);
    }

    /**
     * 同上，延迟在 {@code [loMs, hiMs]} 里随机 —— 抖一下更像真人。
     */
    public static void afterRandom(long loMs, long hiMs, Runnable action) {
        after(randomBetween(loMs, hiMs), action);
    }

    public static Chain chain() {
        return new Chain();
    }

    /**
     * 一串有节拍的动作，整条链在一个虚拟线程上按顺序跑。
     */
    public static final class Chain {

        private final List<Runnable> steps = new ArrayList<>();

        private BooleanSupplier keepGoing;

        private Chain() {
        }

        public Chain run(Runnable action) {
            if (action != null) {
                steps.add(action);
            }
            return this;
        }

        public Chain pause(long ms) {
            long delay = Math.max(0, ms);
            steps.add(() -> parkFor(delay));
            return this;
        }

        public Chain pauseRandom(long loMs, long hiMs) {
            steps.add(() -> parkFor(randomBetween(loMs, hiMs)));
            return this;
        }

        /**
         * 每一步执行前检查一次；返回 false 就静默结束整条链（机器人死了、换图了都能用）。
         */
        public Chain stopUnless(BooleanSupplier condition) {
            this.keepGoing = condition;
            return this;
        }

        /**
         * 立刻返回，链在后台跑。调用方的 tick 不受影响。
         */
        public void start() {
            List<Runnable> script = new ArrayList<>(steps);
            BooleanSupplier gate = keepGoing;
            runVirtual(() -> {
                for (Runnable step : script) {
                    if (gate != null && !gate.getAsBoolean()) {
                        return;
                    }
                    if (!safely(step)) {
                        // 一步抛异常就整条链结束：后续动作通常依赖前一步（比如先 warp 再走）
                        return;
                    }
                }
            });
        }
    }

    private static void runVirtual(Runnable action) {
        Thread.ofVirtual().name("bot-timing-step").start(() -> safely(action));
    }

    private static void parkFor(long ms) {
        try {
            Thread.sleep(ms); // 只挂起虚拟线程
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("BotTiming 链被中断", e);
        }
    }

    private static long randomBetween(long loMs, long hiMs) {
        long lo = Math.max(0, Math.min(loMs, hiMs));
        long hi = Math.max(loMs, hiMs);
        return hi > lo ? lo + ThreadLocalRandom.current().nextLong(hi - lo + 1) : lo;
    }

    private static boolean safely(Runnable action) {
        try {
            action.run();
            return true;
        } catch (Throwable t) {
            log.warn("[BotTiming] 步骤执行失败：{}", t.toString());
            return false;
        }
    }
}
