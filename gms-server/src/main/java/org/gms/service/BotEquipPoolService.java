package org.gms.service;

import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gms.dao.entity.BotEquipGenericPoolDO;
import org.gms.dao.entity.BotEquipNxPoolDO;
import org.gms.dao.entity.BotEquipOmitListDO;
import org.gms.dao.mapper.BotEquipGenericPoolMapper;
import org.gms.dao.mapper.BotEquipNxPoolMapper;
import org.gms.dao.mapper.BotEquipOmitListMapper;
import org.gms.soloMapling.ArtificialPlayer.BotDecoratorSystem.GenericEquipPool;
import org.gms.soloMapling.ArtificialPlayer.BotDecoratorSystem.NXItemPool;
import org.gms.soloMapling.itemPool.EquipOmitList;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.gms.dao.entity.table.BotEquipGenericPoolDOTableDef.BOT_EQUIP_GENERIC_POOL_D_O;
import static org.gms.dao.entity.table.BotEquipNxPoolDOTableDef.BOT_EQUIP_NX_POOL_D_O;
import static org.gms.dao.entity.table.BotEquipOmitListDOTableDef.BOT_EQUIP_OMIT_LIST_D_O;

/**
 * 机器人装备随机池服务。
 *
 * <p>两类调用方：
 * <ul>
 *   <li><b>游戏侧</b>（静态加载器）：{@code loadEnabledXxx()} 只取启用的行，
 *       由 {@code BotConfigBridge} 转交，加载结果缓存在各池的静态字段里；</li>
 *   <li><b>管理端</b>：完整 CRUD，保存/删除后由 Controller 调 {@link #reloadCaches()}
 *       让游戏侧立即生效（不重启）。</li>
 * </ul>
 *
 * <p>为什么重载要放在事务外：{@link #reloadCaches()} 走的是静态池的 forceReload，
 * 内部重新查库；如果在同一个事务里调用，读到的还是提交前的快照，症状是
 * 「保存成功但游戏里还是老数据」。
 *
 * @author WorkBuddy
 * @since 2026-10-17
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BotEquipPoolService {

    public static final String POOL_GENERIC = "GENERIC";
    public static final String POOL_NX = "NX";
    public static final String POOL_OMIT = "OMIT";

    private final BotEquipGenericPoolMapper genericPoolMapper;
    private final BotEquipNxPoolMapper nxPoolMapper;
    private final BotEquipOmitListMapper omitListMapper;

    // ---------------------------------------------------------------- 游戏侧读取

    /**
     * 通用装备池（只含启用行）。
     */
    public List<BotEquipGenericPoolDO> loadEnabledGenericPool() {
        return genericPoolMapper.selectListByQuery(QueryWrapper.create()
                .where(BOT_EQUIP_GENERIC_POOL_D_O.ENABLED.eq(true))
                .orderBy(BOT_EQUIP_GENERIC_POOL_D_O.CATEGORY.asc(), BOT_EQUIP_GENERIC_POOL_D_O.ITEM_ID.asc()));
    }

    /**
     * NX 点装池（只含启用行）。
     */
    public List<BotEquipNxPoolDO> loadEnabledNxPool() {
        return nxPoolMapper.selectListByQuery(QueryWrapper.create()
                .where(BOT_EQUIP_NX_POOL_D_O.ENABLED.eq(true))
                .orderBy(BOT_EQUIP_NX_POOL_D_O.CATEGORY.asc(), BOT_EQUIP_NX_POOL_D_O.ITEM_ID.asc()));
    }

    /**
     * 排除表（只含启用行）。
     */
    public List<BotEquipOmitListDO> loadEnabledOmitList() {
        return omitListMapper.selectListByQuery(QueryWrapper.create()
                .where(BOT_EQUIP_OMIT_LIST_D_O.ENABLED.eq(true))
                .orderBy(BOT_EQUIP_OMIT_LIST_D_O.BLOCK_TYPE.asc(), BOT_EQUIP_OMIT_LIST_D_O.ITEM_ID.asc()));
    }

    // ---------------------------------------------------------------- 管理端查询

    public List<BotEquipGenericPoolDO> listGenericPool(String category) {
        QueryWrapper qw = QueryWrapper.create();
        if (category != null && !category.isBlank()) {
            qw.where(BOT_EQUIP_GENERIC_POOL_D_O.CATEGORY.eq(category));
        }
        qw.orderBy(BOT_EQUIP_GENERIC_POOL_D_O.CATEGORY.asc(), BOT_EQUIP_GENERIC_POOL_D_O.ITEM_ID.asc());
        return genericPoolMapper.selectListByQuery(qw);
    }

    public List<BotEquipNxPoolDO> listNxPool(String category) {
        QueryWrapper qw = QueryWrapper.create();
        if (category != null && !category.isBlank()) {
            qw.where(BOT_EQUIP_NX_POOL_D_O.CATEGORY.eq(category));
        }
        qw.orderBy(BOT_EQUIP_NX_POOL_D_O.CATEGORY.asc(), BOT_EQUIP_NX_POOL_D_O.ITEM_ID.asc());
        return nxPoolMapper.selectListByQuery(qw);
    }

    public List<BotEquipOmitListDO> listOmitList(String blockType) {
        QueryWrapper qw = QueryWrapper.create();
        if (blockType != null && !blockType.isBlank()) {
            qw.where(BOT_EQUIP_OMIT_LIST_D_O.BLOCK_TYPE.eq(blockType));
        }
        qw.orderBy(BOT_EQUIP_OMIT_LIST_D_O.BLOCK_TYPE.asc(), BOT_EQUIP_OMIT_LIST_D_O.ITEM_ID.asc());
        return omitListMapper.selectListByQuery(qw);
    }

    // ---------------------------------------------------------------- 管理端写入

    @Transactional(rollbackFor = Exception.class)
    public void saveGenericPool(BotEquipGenericPoolDO row) {
        if (row == null || row.getCategory() == null || row.getItemId() == null) {
            throw new IllegalArgumentException("category 与 itemId 不能为空");
        }
        if (row.getId() == null) {
            genericPoolMapper.insertSelective(row);
        } else {
            genericPoolMapper.update(row);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveNxPool(BotEquipNxPoolDO row) {
        if (row == null || row.getCategory() == null || row.getItemId() == null) {
            throw new IllegalArgumentException("category 与 itemId 不能为空");
        }
        if (row.getGender() == null) {
            // 唯一键列不能是 NULL，空串表示「按物品 ID 推导性别」
            row.setGender("");
        }
        if (row.getId() == null) {
            nxPoolMapper.insertSelective(row);
        } else {
            nxPoolMapper.update(row);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveOmitList(BotEquipOmitListDO row) {
        if (row == null) {
            throw new IllegalArgumentException("参数不能为空");
        }
        if (row.getBlockType() == null || row.getBlockType().isBlank()) {
            row.setBlockType("ID");
        }
        if ("RANGE".equalsIgnoreCase(row.getBlockType())) {
            if (row.getRangeStart() == null || row.getRangeEnd() == null) {
                throw new IllegalArgumentException("区间块必须填 rangeStart 与 rangeEnd");
            }
            row.setItemId(0);
        } else {
            if (row.getItemId() == null || row.getItemId() <= 0) {
                throw new IllegalArgumentException("单条块必须填 itemId");
            }
            row.setRangeStart(0);
            row.setRangeEnd(0);
        }
        if (row.getNote() == null) {
            row.setNote("");
        }
        if (row.getId() == null) {
            omitListMapper.insertSelective(row);
        } else {
            omitListMapper.update(row);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRows(String poolType, List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids 不能为空");
        }
        switch (poolType == null ? "" : poolType.toUpperCase()) {
            case POOL_GENERIC -> ids.forEach(genericPoolMapper::deleteById);
            case POOL_NX -> ids.forEach(nxPoolMapper::deleteById);
            case POOL_OMIT -> ids.forEach(omitListMapper::deleteById);
            default -> throw new IllegalArgumentException("未知的 poolType: " + poolType);
        }
    }

    /**
     * 让游戏侧立即用上新数据（清静态缓存后重新查库）。
     * 由 Controller 在保存/删除事务提交之后调用。
     */
    public void reloadCaches() {
        GenericEquipPool.forceReload();
        NXItemPool.forceReload();
        EquipOmitList.forceReload();
        log.info("[BotEquipPool] 三个装备池已重新从数据库加载");
    }
}
