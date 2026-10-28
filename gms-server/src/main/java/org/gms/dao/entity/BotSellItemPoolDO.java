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
 * 摆摊出售机器人的货架物品池 实体类。
 *
 * <p>数据源是 SoloMapling 的 {@code itemConfig/*.yaml}（一次性导出 123 条，之后只读数据库）。
 * 与装备池不同，这里的每一行都自带「价钱 + 每组数量」，因为摊子（{@code PlayerShop}）
 * 的每个格子就是一个 {@code PlayerShopItem(item, bundles, price)}：
 * 买家付 {@code price * bundles}，拿到 {@code item.quantity * bundles} 个。
 *
 * <p>⚠️ {@code category} 只是个分池标签（卷轴 / 暗黑卷轴 / 飞镖 / 药水 / 矿石 / 装备），
 * 抽签时按分类轮流抽，避免一次上架整摊货全是药水。
 *
 * <p>装备行（{@code category = "equip"}）由服务端构造成真正的 {@code Equip} 再上架，
 * 一格只能放一件；{@code price} 填 0 表示按 WZ 售价自动定价。
 *
 * @author WorkBuddy
 * @since 2026-10-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("bot_sell_item_pool")
public class BotSellItemPoolDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Integer id;

    /**
     * scroll / darkscroll / star / potion / ore / equip
     */
    private String category;

    /**
     * 物品 ID
     */
    private Integer itemId;

    /**
     * 每格单价（mesos）
     */
    @Builder.Default
    private Integer price = 0;

    /**
     * 每格数量下限
     */
    @Builder.Default
    private Integer qtyMin = 1;

    /**
     * 每格数量上限
     */
    @Builder.Default
    private Integer qtyMax = 1;

    /**
     * 抽签权重，越大越容易被摆出来
     */
    @Builder.Default
    private Integer weight = 100;

    /**
     * 是否参与抽取
     */
    @Builder.Default
    private boolean enabled = true;

    /**
     * 物品名/备注，仅后台展示用
     */
    @Builder.Default
    private String note = "";
}
