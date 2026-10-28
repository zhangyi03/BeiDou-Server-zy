package org.gms.service;

import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gms.dao.entity.BotDialoguePoolDO;
import org.gms.dao.mapper.BotDialoguePoolMapper;
import org.gms.soloMapling.ArtificialPlayer.BotDialogueSystem.BotDialoguePool;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.gms.dao.entity.table.BotDialoguePoolDOTableDef.BOT_DIALOGUE_POOL_D_O;

/**
 * 机器人对话池服务（{@code bot_dialogue_pool}）。
 *
 * <p>与 {@link BotSellPoolService} 同一套分工：游戏侧通过
 * {@code BotConfigBridge.dialoguePool(type)} 拿<b>只含启用行</b>的列表，缓存在
 * {@link BotDialoguePool} 的静态字段里；管理端做完整 CRUD，保存/删除后由 Controller
 * 调 {@link #reloadCaches()}，下一摊 / 下一句喊话立即用新文案。
 *
 * <p>重载放在事务外：在事务内 forceReload 会读到提交前的快照，症状是
 * 「保存成功但摊位还是老名字」。
 *
 * @author WorkBuddy
 * @since 2026-10-28
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BotDialoguePoolService {

    /**
     * 摆摊名称池
     */
    public static final String POOL_SHOP_NAME = BotDialoguePool.POOL_SHOP_NAME;

    /**
     * 喊话内容池
     */
    public static final String POOL_SHOUT = BotDialoguePool.POOL_SHOUT;

    private final BotDialoguePoolMapper dialoguePoolMapper;

    // ---------------------------------------------------------------- 游戏侧读取

    /**
     * 某个池的启用行（游戏侧用）。
     */
    public List<BotDialoguePoolDO> loadEnabledPool(String poolType) {
        return dialoguePoolMapper.selectListByQuery(QueryWrapper.create()
                .where(BOT_DIALOGUE_POOL_D_O.ENABLED.eq(true))
                .and(BOT_DIALOGUE_POOL_D_O.POOL_TYPE.eq(poolType))
                .orderBy(BOT_DIALOGUE_POOL_D_O.ID.asc()));
    }

    // ---------------------------------------------------------------- 管理端查询

    public List<BotDialoguePoolDO> listPool(String poolType) {
        QueryWrapper qw = QueryWrapper.create();
        if (poolType != null && !poolType.isBlank()) {
            qw.where(BOT_DIALOGUE_POOL_D_O.POOL_TYPE.eq(poolType));
        }
        qw.orderBy(BOT_DIALOGUE_POOL_D_O.POOL_TYPE.asc(), BOT_DIALOGUE_POOL_D_O.ID.asc());
        return dialoguePoolMapper.selectListByQuery(qw);
    }

    // ---------------------------------------------------------------- 管理端写入

    @Transactional(rollbackFor = Exception.class)
    public void saveRow(BotDialoguePoolDO row) {
        if (row == null || row.getPoolType() == null || row.getPoolType().isBlank()
                || row.getContent() == null || row.getContent().isBlank()) {
            throw new IllegalArgumentException("poolType 与 content 不能为空");
        }
        row.setPoolType(row.getPoolType().trim());
        row.setContent(row.getContent().trim());
        if (row.getContent().length() > 200) {
            row.setContent(row.getContent().substring(0, 200));
        }
        if (row.getWeight() == null || row.getWeight() < 1) {
            row.setWeight(100);
        }
        if (row.getNote() == null) {
            row.setNote("");
        }
        if (row.getId() == null) {
            dialoguePoolMapper.insertSelective(row);
        } else {
            dialoguePoolMapper.update(row);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRows(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids 不能为空");
        }
        ids.forEach(dialoguePoolMapper::deleteById);
    }

    /**
     * 让游戏侧立即用上新文案。由 Controller 在保存/删除事务提交之后调用。
     */
    public void reloadCaches() {
        BotDialoguePool.forceReload();
        log.info("[BotDialoguePool] 对话池已重新从数据库加载");
    }
}
