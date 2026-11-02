package org.gms.soloMapling.server;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;

/**
 * 机器人主循环：一个线程轮扫所有机器人，按各自的周期推进它们的 tick。
 *
 * <p>为什么不是「每个机器人一个线程」：一台服上几十个机器人就是几十个 OS 线程，
 * 线程上下文切换和栈内存都不划算，而且很难统一限速。这里改成「一个巡检线程 + 虚拟线程派发」：
 * <ul>
 *   <li>巡检线程每 {@value #DRIVER_PERIOD_MS}ms 扫一遍到期表，只做廉价的 volatile 读；</li>
 *   <li>到期的 tick 丢给虚拟线程跑，某个机器人 tick 卡住（比如里面睡了 1 秒）也只挂起它自己；</li>
 *   <li>每个机器人一个 CAS 标记，保证同一个机器人绝不会有两个 tick 同时跑；</li>
 *   <li>下一次到期时间从「本次 tick 结束」开始算，慢 tick 不会堆积。</li>
 * </ul>
 *
 * <p>首次注册时懒启动巡检线程（daemon），服务关闭时随进程一起消失，不需要 Spring 生命周期。
 *
 * @author WorkBuddy
 * @since 2026-11-02
 */
@Slf4j
public final class BotTickService {

    /**
     * 巡检节拍。越小越灵敏，越大越省 CPU；100ms 对机器人这种量级足够。
     */
    private static final long DRIVER_PERIOD_MS = 100L;

    /**
     * 单个机器人两帧之间的默认间隔。
     */
    public static final long DEFAULT_PERIOD_MS = 500L;

    private static final class Entry {

        private final Runnable tick;

        private volatile long periodMs;

        private volatile long nextDueMs;

        private final AtomicBoolean ticking = new AtomicBoolean(false);

        private Entry(Runnable tick, long periodMs, long nextDueMs) {
            this.tick = tick;
            this.periodMs = periodMs;
            this.nextDueMs = nextDueMs;
        }
    }

    private static final Map<Integer, Entry> ENTRIES = new ConcurrentHashMap<>();

    private static final AtomicBoolean DRIVER_STARTED = new AtomicBoolean(false);

    private BotTickService() {
    }

    /**
     * 注册（或替换）一个机器人的 tick。
     *
     * @param botId     角色 id，做键用
     * @param tick      每次到期执行的动作
     * @param periodMs  周期，{@code <= 0} 时用 {@link #DEFAULT_PERIOD_MS}
     */
    public static void register(int botId, Runnable tick, long periodMs) {
        if (tick == null) {
            return;
        }
        long period = periodMs <= 0 ? DEFAULT_PERIOD_MS : periodMs;
        // 首次到期留一个随机抖动，避免同一批上线的机器人永远同帧
        long firstDue = System.currentTimeMillis() + (long) (Math.random() * period);
        ENTRIES.put(botId, new Entry(tick, period, firstDue));
        ensureDriver();
    }

    public static void unregister(int botId) {
        ENTRIES.remove(botId);
    }

    public static boolean isRegistered(int botId) {
        return ENTRIES.containsKey(botId);
    }

    /**
     * 调整某个机器人的周期（战斗中加快、闲逛时放慢）。
     */
    public static void setPeriod(int botId, long periodMs) {
        Entry entry = ENTRIES.get(botId);
        if (entry != null && periodMs > 0) {
            entry.periodMs = periodMs;
        }
    }

    /**
     * 让它「马上」跑一帧（状态切换后不想等一个周期）。
     */
    public static void nudge(int botId) {
        Entry entry = ENTRIES.get(botId);
        if (entry != null) {
            entry.nextDueMs = System.currentTimeMillis();
        }
    }

    /**
     * 进程退出 / 重载机器人的时候全清。
     */
    public static void clear() {
        ENTRIES.clear();
    }

    private static void ensureDriver() {
        if (!DRIVER_STARTED.compareAndSet(false, true)) {
            return;
        }
        Thread driver = new Thread(BotTickService::drive, "bot-tick-driver");
        driver.setDaemon(true);
        driver.start();
        log.info("[BotTickService] 机器人 tick 巡检线程已启动（{}ms 一轮）", DRIVER_PERIOD_MS);
    }

    private static void drive() {
        while (true) {
            try {
                long now = System.currentTimeMillis();
                for (Map.Entry<Integer, Entry> pair : ENTRIES.entrySet()) {
                    Entry entry = pair.getValue();
                    if (now < entry.nextDueMs || !entry.ticking.compareAndSet(false, true)) {
                        continue;
                    }
                    int botId = pair.getKey();
                    Thread.ofVirtual().name("bot-tick-" + botId).start(() -> runEntry(botId, entry));
                }
                Thread.sleep(DRIVER_PERIOD_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable t) {
                // 巡检线程不能死：死一次所有机器人都停
                log.error("[BotTickService] 巡检异常，继续下一轮", t);
            }
        }
    }

    private static void runEntry(int botId, Entry entry) {
        try {
            entry.tick.run();
        } catch (Throwable t) {
            log.warn("[BotTickService] 机器人 {} 的 tick 抛异常：{}", botId, t.toString());
        } finally {
            // 从「本次结束」算下一次，慢 tick 不会堆在自己后面
            entry.nextDueMs = System.currentTimeMillis() + entry.periodMs;
            entry.ticking.set(false);
        }
    }
}
