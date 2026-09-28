package org.gms.net.server.task;

import lombok.extern.slf4j.Slf4j;
import org.gms.manager.ServerManager;
import org.gms.service.BossRespawnConfigService;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 野外Boss刷新配置热更新任务。
 * <p>
 * 直接改数据库（SQL/DB 工具）不会刷新 {@code BossRespawnConfigCache}，
 * 而缓存只在服务启动时加载一次，导致"改了库但没生效"。
 * 本任务每 1 分钟比对一次数据库与缓存的差异，一旦发现变更就刷新缓存并重建
 * AreaBoss 事件的排期，从而做到改库后无需重启、也不用打任何命令即可生效。
 * <p>
 * 不可重入保护：上一轮未结束时下一轮直接跳过，避免单轮卡顿后
 * scheduleAtFixedRate 追赶积压，造成多轮重复重建排期。
 *
 * @author WorkBuddy
 * @since 2026-09-24
 */
@Slf4j
public class BossRespawnConfigRefreshTask implements Runnable {

    private static final AtomicBoolean running = new AtomicBoolean(false);

    @Override
    public void run() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            // 延迟取 bean：任务对象由 Server.init 注册，此时 Spring 上下文已就绪
            BossRespawnConfigService service = ServerManager.getApplicationContext().getBean(BossRespawnConfigService.class);
            // refreshIfChanged 内部已吞掉异常，这里只兜底防止 Bean 获取失败打断周期任务
            service.refreshIfChanged();
        } catch (Exception e) {
            log.error("BossRespawnConfigRefreshTask 执行失败", e);
        } finally {
            running.set(false);
        }
    }
}
