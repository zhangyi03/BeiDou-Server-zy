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
import java.util.Date;

/**
 * 机器人档案表 实体类。
 * <p>
 * 机器人按真实玩家生成：{@code account_id} 指向自动创建的机器人专用账号，
 * {@code character_id} 指向 {@code characters} 表里的真实角色行。
 * 因此等级 / 职业 / 性别 / 装备一律以 {@code characters} + {@code inventoryitems} 为准，
 * 本表只存「出生点 + 开机自启 + 行为类型」这类玩家表里没有的东西，避免双份真相。
 *
 * @author WorkBuddy
 * @since 2026-10-17
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("bot_profile")
public class BotProfileDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 自增id
     */
    @Id(keyType = KeyType.Auto)
    private Integer id;

    /**
     * characters.id，真实角色行
     */
    private Integer characterId;

    /**
     * accounts.id，机器人专用账号
     */
    private Integer accountId;

    /**
     * 出生地图
     */
    @Builder.Default
    private Integer mapId = 910000000;

    /**
     * 出生点 X
     */
    @Builder.Default
    private Integer spawnX = 0;

    /**
     * 出生点 Y
     */
    @Builder.Default
    private Integer spawnY = 0;

    /**
     * 1=开机自动上线
     */
    @Builder.Default
    private boolean autoStart = false;

    /**
     * 行为类型码，取值见 {@link org.gms.soloMapling.ArtificialPlayer.BotType}
     * （IDLE / TRAINING_BOT / FM_BOT / ... 与上游 SoloMapling 的类型一一对应）。
     * <p>
     * 目前只做登记与展示：行为逻辑尚未移植，上线一律走通用生成/装饰链路。
     */
    @Builder.Default
    private String botType = "IDLE";

    /**
     * 备注
     */
    private String note;

    /**
     * 1=外观（发型 / 脸 / 肤色）已随机生成并落库。
     * <p>
     * 置 1 后再上线不会重新捏外观：装备栏为空时只补装备，外观永远以 characters 表为准。
     * 首次生成时装备和外观一起随机、一起落库。
     */
    @Builder.Default
    private boolean decorated = false;

    /**
     * 人设档（S/A/B/C/D），装饰时决定发型与装备池的抽取范围，随首次生成固化。
     */
    @Builder.Default
    private String botTier = "S";

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;
}
