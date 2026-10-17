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
 * 机器人禁止自动穿戴的装备 实体类。
 * <p>
 * 数据源是 SoloMapling 的 {@code EquipOmitList.yaml}（144 条 ID + 可选区间块）。
 * 这些是 WZ 里完全合法、但穿在机器人身上很难看的装备（各国国旗当武器挥等）。
 * <p>
 * {@code block_type = ID} 时用 {@code item_id}；{@code block_type = RANGE} 时用
 * {@code range_start~range_end} 闭区间，另一组列保持 0。
 *
 * @author WorkBuddy
 * @since 2026-10-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("bot_equip_omit_list")
public class BotEquipOmitListDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Integer id;

    /**
     * ID=单条；RANGE=闭区间
     */
    @Builder.Default
    private String blockType = "ID";

    /**
     * block_type=ID 时生效
     */
    @Builder.Default
    private Integer itemId = 0;

    /**
     * block_type=RANGE 时生效
     */
    @Builder.Default
    private Integer rangeStart = 0;

    /**
     * block_type=RANGE 时生效
     */
    @Builder.Default
    private Integer rangeEnd = 0;

    /**
     * 物品名/说明
     */
    @Builder.Default
    private String note = "";

    /**
     * 是否启用
     */
    @Builder.Default
    private boolean enabled = true;
}
