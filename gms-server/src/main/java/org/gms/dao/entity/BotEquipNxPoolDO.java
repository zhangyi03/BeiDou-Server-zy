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
 * 机器人 NX 点装随机池 实体类。
 * <p>
 * 数据源是 SoloMapling 的 {@code NXItemPool.yaml}（169 条 / 12 个分类）。
 * {@code gender} 为空串时按物品 ID 第四位推导（0=男 1=女 2=通用），
 * 这是 v83 客户端的固有约定，不是缺省值的偷懒。
 * <p>
 * ⚠️ 唯一键列用空串而非 NULL：MySQL 的唯一键对 NULL 不生效，能插进重复行。
 *
 * @author WorkBuddy
 * @since 2026-10-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("bot_equip_nx_pool")
public class BotEquipNxPoolDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Integer id;

    /**
     * caps / tops / bottoms / overalls / weapons / shoes / gloves / capes / earrings / face / eye / rings
     */
    private String category;

    /**
     * 点装物品 ID
     */
    private Integer itemId;

    /**
     * male / female / unisex，空串=按物品 ID 第四位推导
     */
    @Builder.Default
    private String gender = "";

    /**
     * 是否启用
     */
    @Builder.Default
    private boolean enabled = true;
}
