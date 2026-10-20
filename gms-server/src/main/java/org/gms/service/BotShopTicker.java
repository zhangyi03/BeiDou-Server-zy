package org.gms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gms.soloMapling.ArtificialPlayer.BotShopSystem.BotShop;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * 摆摊出售机器人的摊位维护线程。
 *
 * <p>每 5 秒 tick 一次，做两件事：
 * <ol>
 *   <li><b>补货重开</b>——摊子被卖光时主线会自己关摊并清空 {@code chr.playerShop}，
 *       这里发现为空就按池子重新抽货开张；</li>
 *   <li><b>喊话招揽</b>——每个机器人每 12~20 秒浮一句广告（{@code GET_CHAT_TEXT} 地图广播）。</li>
 * </ol>
 *
 * <p>为什么用裸 daemon 线程而不是 {@code @Scheduled}：本项目没有开
 * {@code @EnableScheduling}，为了这一个定时任务去开全局调度没必要；线程也命名为
 * {@code bot-shop-ticker} 便于 jstack 里辨认。服务就绪（{@link ApplicationReadyEvent}）
 * 才启动，和 {@code BotService} 保持同一时机，避免抢在 {@code ServerManager} 之前碰世界对象。
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BotShopTicker implements ApplicationListener<ApplicationReadyEvent> {

    private static final long TICK_INTERVAL_MS = 5_000L;

    private volatile boolean started = false;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        start();
    }

    private synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        Thread thread = new Thread(this::loop, "bot-shop-ticker");
        thread.setDaemon(true);
        thread.start();
        log.info("[BotShopTicker] 摊位维护线程已启动，间隔 {} ms", TICK_INTERVAL_MS);
    }

    private void loop() {
        while (true) {
            try {
                BotShop.tick();
            } catch (Throwable t) {
                // 一个机器人的异常不能打死整条线程
                log.warn("[BotShopTicker] tick 异常：{}", t.getMessage(), t);
            }
            try {
                Thread.sleep(TICK_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("[BotShopTicker] 线程被中断，退出");
                return;
            }
        }
    }
}
