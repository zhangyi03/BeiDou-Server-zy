package org.gms.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 野外Boss刷新配置 DTO。
 *
 * @author WorkBuddy
 * @since 2026-09-23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BossRespawnConfigDTO {

    /**
     * 自增id
     */
    private Integer id;

    /**
     * 事件脚本名（不带.js后缀），如 AreaBossMano，唯一键
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
     * 刷新间隔(分钟)，提交时 <=0 表示沿用脚本内 BossTime
     */
    private Integer spawnInterval;

    /**
     * 生成X坐标，提交 null 表示沿用脚本内 point
     */
    private Integer pointX;

    /**
     * 生成Y坐标，提交 null 表示沿用脚本内 point
     */
    private Integer pointY;

    /**
     * 登场公告，提交 null 或空 表示沿用脚本内 BossNotice
     */
    private String notice;

    /**
     * 是否启用；提交时省略视为启用
     */
    private Boolean enabled;

    /**
     * 实际生效间隔(分钟)，仅返回给前端展示，提交时忽略。
     * 等于 spawnInterval × boss_respawn_mob_time_rate；spawnInterval <= 0 时为 null，代表用脚本默认值。
     */
    private Integer effectiveMinutes;

    /**
     * 当前全局刷新时间倍率 boss_respawn_mob_time_rate，仅返回给前端展示，提交时忽略
     */
    private Float timeRate;
}
