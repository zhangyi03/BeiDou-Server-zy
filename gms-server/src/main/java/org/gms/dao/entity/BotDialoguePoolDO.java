package org.gms.dao.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 机器人对话池 实体类（{@code bot_dialogue_pool}）。
 *
 * <p>一个表装两类文案，靠 {@code pool_type} 区分：
 * <ul>
 *   <li>{@code shop_name}——摆摊名称，开摊时抽一条当个人商店的摊位标题；</li>
 *   <li>{@code shout}——喊话内容，摊主每隔 12~20 秒原地喊一句。</li>
 * </ul>
 *
 * <p>游戏侧由 {@code BotDialoguePool} 缓存启用行；管理端保存/删除后由
 * {@code BotDialoguePoolService#reloadCaches()} 触发热重载，不用重启服务端。
 *
 * @author WorkBuddy
 * @since 2026-10-28
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("bot_dialogue_pool")
public class BotDialoguePoolDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Integer id;

    /**
     * shop_name / shout
     */
    private String poolType;

    /**
     * 文案正文（摆摊名称建议 20 字以内，摊位框只有一行）
     */
    private String content;

    /**
     * 抽签权重，越大越容易被抽到
     */
    @Builder.Default
    private Integer weight = 100;

    /**
     * 是否参与抽取
     */
    @Builder.Default
    private boolean enabled = true;

    /**
     * 备注，仅后台展示
     */
    @Builder.Default
    private String note = "";
}
