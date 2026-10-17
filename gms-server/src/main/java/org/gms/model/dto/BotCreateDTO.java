package org.gms.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 新建机器人请求。
 * <p>
 * 机器人按真实玩家生成：服务端会用这些参数建 {@code accounts} + {@code characters} 真实行，
 * 等级 / 职业 / 性别留空则由档位逻辑随机（S/A/B/C/D 档对应不同等级段）。
 *
 * @author WorkBuddy
 * @since 2026-10-17
 */
@Data
public class BotCreateDTO implements Serializable {

    /**
     * 角色名，留空自动生成（字母数字 4~13 位）
     */
    private String name;

    /**
     * 目标等级，留空按档位在 10~80 内随机
     */
    private Integer level;

    /**
     * 职业 id，留空按等级随机
     */
    private Integer job;

    /**
     * 0=男 1=女，留空随机
     */
    private Integer gender;

    /**
     * 出生地图，默认自由市场
     */
    private Integer mapId;

    private Integer spawnX;

    private Integer spawnY;

    /**
     * 是否开机自动上线
     */
    private Boolean autoStart;

    /**
     * 机器人行为类型码，取值见 {@code org.gms.soloMapling.ArtificialPlayer.BotType}，
     * 留空按 {@code IDLE}（站桩待机）
     */
    private String botType;

    private String note;
}
