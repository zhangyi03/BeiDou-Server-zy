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
 * 机器人通用装备随机池 实体类。
 * <p>
 * 数据源是 SoloMapling 的 {@code GenericEquipPool.yaml}（127 条 / 8 个分类），
 * 现在只从数据库读，YAML 已删除，不存在文件回退。
 *
 * @author WorkBuddy
 * @since 2026-10-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("bot_equip_generic_pool")
public class BotEquipGenericPoolDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Integer id;

    /**
     * tops / bottoms / overalls / weapons / caps / shoes / gloves / capes
     */
    private String category;

    /**
     * 装备物品 ID
     */
    private Integer itemId;

    /**
     * 是否启用
     */
    @Builder.Default
    private boolean enabled = true;
}
