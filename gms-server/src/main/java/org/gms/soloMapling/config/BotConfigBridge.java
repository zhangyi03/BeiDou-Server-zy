package org.gms.soloMapling.config;

import lombok.extern.slf4j.Slf4j;
import org.gms.dao.entity.BotEquipGenericPoolDO;
import org.gms.dao.entity.BotEquipNxPoolDO;
import org.gms.dao.entity.BotEquipOmitListDO;
import org.gms.dao.entity.BotSellItemPoolDO;
import org.gms.service.BotEquipPoolService;
import org.gms.service.BotSellPoolService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 游戏侧（静态方法）访问机器人配置的桥。
 *
 * <p>为什么需要它：{@code GenericEquipPool.load()} / {@code NXItemPool.load()} /
 * {@code EquipOmitList.load()} / {@code SellItemPool.load()} 都是从静态上下文被调用的（{@code BotDecorate} 里是 static 方法），
 * 拿不到 Spring 注入，只能通过 {@link ApplicationContextAware} 把容器挂到静态字段上，
 * 再由这里懒取 bean。
 *
 * <p>容器还没就绪时返回空集合，并打 <b>error</b> 日志 —— 纯 DB 版没有文件回退，
 * 「库空」只可能是种子没灌或表名写错，必须能一眼看见，不能静默变成「机器人不穿衣服」。
 *
 * @author WorkBuddy
 * @since 2026-10-17
 */
@Slf4j
@Component
public class BotConfigBridge implements ApplicationContextAware {

    private static volatile ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        context = applicationContext;
    }

    private static BotEquipPoolService poolService() {
        ApplicationContext ctx = context;
        if (ctx == null) {
            log.error("BotConfigBridge: Spring 上下文未就绪，机器人装备池读取失败（本次返回空集合）");
            return null;
        }
        try {
            return ctx.getBean(BotEquipPoolService.class);
        } catch (Exception e) {
            log.error("BotConfigBridge: 取 BotEquipPoolService 失败", e);
            return null;
        }
    }

    /**
     * 通用装备池（只返回启用的行）。
     */
    public static List<BotEquipGenericPoolDO> genericPool() {
        BotEquipPoolService service = poolService();
        if (service == null) {
            return Collections.emptyList();
        }
        return service.loadEnabledGenericPool();
    }

    /**
     * NX 点装池（只返回启用的行）。
     */
    public static List<BotEquipNxPoolDO> nxPool() {
        BotEquipPoolService service = poolService();
        if (service == null) {
            return Collections.emptyList();
        }
        return service.loadEnabledNxPool();
    }

    /**
     * 排除表（只返回启用的行）。
     */
    public static List<BotEquipOmitListDO> omitList() {
        BotEquipPoolService service = poolService();
        if (service == null) {
            return Collections.emptyList();
        }
        return service.loadEnabledOmitList();
    }

    /**
     * 摆摊出售物品池（只返回启用的行）。
     */
    public static List<BotSellItemPoolDO> sellPool() {
        ApplicationContext ctx = context;
        if (ctx == null) {
            log.error("BotConfigBridge: Spring 上下文未就绪，机器人出售物品池读取失败（本次返回空集合）");
            return Collections.emptyList();
        }
        try {
            return ctx.getBean(BotSellPoolService.class).loadEnabledPool();
        } catch (Exception e) {
            log.error("BotConfigBridge: 取 BotSellPoolService 失败", e);
            return Collections.emptyList();
        }
    }
}
