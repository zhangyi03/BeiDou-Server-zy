package org.gms.soloMapling.ArtificialPlayer;

import lombok.extern.slf4j.Slf4j;
import org.gms.client.Character;
import org.gms.soloMapling.server.BotTickService;

/**
 * 机器人「大脑」基类：一个按帧推进的状态机。
 *
 * <p>上游的对应物是 {@code BotSM}（560 行），但它同时背着交易系统、事件总线、视距统计、
 * 投掷星选择等一堆与「行为」无关的东西。本移植版先给出一层最小的壳：
 * <ul>
 *   <li>由 {@code BotTickService} 定期调用 {@link #tick()}；</li>
 *   <li>{@link #waitFor(long)} 让整段状态机停摆到下个节拍 —— 比在 tick 里 sleep 好，
 *       既不占线程，也不会阻塞别的机器人；</li>
 *   <li>子类只实现 {@link #updateState()}，不要自己起线程。</li>
 * </ul>
 *
 * <p>约定：{@code updateState()} 里一次只做一小步（走一步、打一下、看一眼），
 * 需要等就 {@code waitFor} 然后 return，下一帧接着来。
 *
 * @author WorkBuddy
 * @since 2026-11-02
 */
@Slf4j
public abstract class BotBrain {

    protected final Character chr;

    private volatile long waitUntilMs;

    private volatile boolean running = true;

    protected BotBrain(Character chr) {
        if (chr == null) {
            throw new IllegalArgumentException("BotBrain 需要一个角色");
        }
        this.chr = chr;
    }

    public Character getChr() {
        return chr;
    }

    /**
     * 每帧的实际行为。子类实现。
     */
    public abstract void updateState();

    /**
     * 这个大脑希望多久跑一帧。
     *
     * <p>默认沿用 {@code BotTickService.DEFAULT_PERIOD_MS}（500ms），适合摆摊这种慢节奏类型；
     * 需要连续走位的类型（比如组队任务跑图）可以调快，但<b>必须同步调小走步距离</b>，
     * 否则每帧跨得太大，客户端只会看到一串瞬移。
     */
    public long getTickPeriodMs() {
        return BotTickService.DEFAULT_PERIOD_MS;
    }

    /**
     * 大脑名字，只用于日志。
     */
    public abstract String getBrainName();

    /**
     * 由 tick 驱动调用，外部不要直接调。
     */
    public final void tick() {
        if (!running || isWaiting()) {
            return;
        }
        if (!isOnline()) {
            // 下线了就别再动了（BotService 会在 despawn 时注销 tick，这里只是兜底）
            running = false;
            return;
        }
        updateState();
    }

    /**
     * 让整段状态机暂停 {@code ms} 毫秒。期间 tick 直接返回。
     */
    public void waitFor(long ms) {
        if (ms > 0) {
            waitUntilMs = System.currentTimeMillis() + ms;
        }
    }

    public void waitForRandom(long loMs, long hiMs) {
        long lo = Math.min(loMs, hiMs);
        long hi = Math.max(loMs, hiMs);
        waitFor(lo + (long) (Math.random() * (hi - lo + 1)));
    }

    public boolean isWaiting() {
        return System.currentTimeMillis() < waitUntilMs;
    }

    /**
     * 立刻解除等待（比如队长刚换图，不能再傻等）。
     */
    public void clearWait() {
        waitUntilMs = 0L;
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean value) {
        this.running = value;
    }

    /**
     * 角色还挂在地图上就算在线。机器人没有 socket，不能看 client。
     */
    public boolean isOnline() {
        return chr.getMap() != null;
    }

    protected void logf(String format, Object... args) {
        log.info("[{} {}] {}", getBrainName(), chr.getName(), String.format(format, args));
    }
}
