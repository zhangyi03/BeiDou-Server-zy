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
 * 野外Boss刷新配置表 实体类。
 * <p>
 * 唯一键是事件脚本名（eventName，不带 .js 后缀），与 EventManager.getName() 对应，
 * 不能用 bossId 做键：同一 bossId 可能被多个脚本共用（提莫 5220003 占 Timer1/2/3）。
 *
 * @author WorkBuddy
 * @since 2026-09-23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("boss_respawn_config")
public class BossRespawnConfigDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 自增id
     */
    @Id(keyType = KeyType.Auto)
    private Integer id;

    /**
     * 事件脚本名（不带.js后缀），如 AreaBossMano
     */
    private String eventName;

    /**
     * 怪物ID
     */
    private Integer bossId;

    /**
     * 地图ID
     */
    private Integer mapId;

    /**
     * 备注名，空则取怪物名
     */
    private String bossName;

    /**
     * 刷新间隔(分钟)，<=0 表示用脚本内的 BossTime
     */
    private Integer spawnInterval;

    /**
     * 生成X坐标，NULL 表示用脚本内的 point
     */
    private Integer pointX;

    /**
     * 生成Y坐标，NULL 表示用脚本内的 point
     */
    private Integer pointY;

    /**
     * 登场公告，NULL 或空 表示用脚本内的 BossNotice
     */
    private String notice;

    /**
     * 是否启用，false=不再排期生成（已生成的Boss不受影响）
     * <p>
     * 必须使用基本类型：脚本引擎（GraalJS）中 Java 的 Boolean 包装对象恒为真值，
     * 写成 Boolean 会导致脚本里 !cfg.enabled 判断失效。
     */
    @Builder.Default
    private boolean enabled = true;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;
}
