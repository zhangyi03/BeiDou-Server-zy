package org.gms.service;

import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gms.dao.entity.BotSellItemPoolDO;
import org.gms.dao.mapper.BotSellItemPoolMapper;
import org.gms.soloMapling.ArtificialPlayer.BotShopSystem.SellItemPool;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.gms.dao.entity.table.BotSellItemPoolDOTableDef.BOT_SELL_ITEM_POOL_D_O;

/**
 * 摆摊出售物品池服务（{@code bot_sell_item_pool}）。
 *
 * <p>与 {@link BotEquipPoolService} 同一套分工：
 * 游戏侧通过 {@code BotConfigBridge.sellPool()} 拿<b>只含启用行</b>的列表并缓存在
 * {@link SellItemPool} 的静态字段里；管理端做完整 CRUD，保存/删除后由 Controller
 * 调 {@link #reloadCaches()}，摊子下一轮补货即用新池，不用重启。
 *
 * <p>重载同样放在事务外：在事务内 forceReload 会读到提交前的快照，
 * 症状是「保存成功但摊子还是老货」。
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BotSellPoolService {

    public static final String POOL_SELL = "SELL";

    private final BotSellItemPoolMapper sellItemPoolMapper;

    // ---------------------------------------------------------------- 游戏侧读取

    /**
     * 出售物品池（只含启用行）。
     */
    public List<BotSellItemPoolDO> loadEnabledPool() {
        return sellItemPoolMapper.selectListByQuery(QueryWrapper.create()
                .where(BOT_SELL_ITEM_POOL_D_O.ENABLED.eq(true))
                .orderBy(BOT_SELL_ITEM_POOL_D_O.CATEGORY.asc(), BOT_SELL_ITEM_POOL_D_O.ITEM_ID.asc()));
    }

    // ---------------------------------------------------------------- 管理端查询

    public List<BotSellItemPoolDO> listPool(String category) {
        QueryWrapper qw = QueryWrapper.create();
        if (category != null && !category.isBlank()) {
            qw.where(BOT_SELL_ITEM_POOL_D_O.CATEGORY.eq(category));
        }
        qw.orderBy(BOT_SELL_ITEM_POOL_D_O.CATEGORY.asc(), BOT_SELL_ITEM_POOL_D_O.ITEM_ID.asc());
        return sellItemPoolMapper.selectListByQuery(qw);
    }

    // ---------------------------------------------------------------- 管理端写入

    @Transactional(rollbackFor = Exception.class)
    public void saveRow(BotSellItemPoolDO row) {
        if (row == null || row.getCategory() == null || row.getCategory().isBlank()
                || row.getItemId() == null || row.getItemId() <= 0) {
            throw new IllegalArgumentException("category 与 itemId 不能为空");
        }
        if (row.getPrice() == null || row.getPrice() < 0) {
            row.setPrice(0);
        }
        if (row.getQtyMin() == null || row.getQtyMin() < 1) {
            row.setQtyMin(1);
        }
        if (row.getQtyMax() == null || row.getQtyMax() < row.getQtyMin()) {
            row.setQtyMax(row.getQtyMin());
        }
        if (row.getWeight() == null || row.getWeight() < 1) {
            row.setWeight(100);
        }
        if (row.getNote() == null) {
            row.setNote("");
        }
        if (row.getId() == null) {
            sellItemPoolMapper.insertSelective(row);
        } else {
            sellItemPoolMapper.update(row);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRows(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids 不能为空");
        }
        ids.forEach(sellItemPoolMapper::deleteById);
    }

    /**
     * 让游戏侧立即用上新数据。由 Controller 在保存/删除事务提交之后调用。
     */
    public void reloadCaches() {
        SellItemPool.forceReload();
        log.info("[BotSellPool] 出售物品池已重新从数据库加载");
    }
}
