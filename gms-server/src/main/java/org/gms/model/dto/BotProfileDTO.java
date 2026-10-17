package org.gms.model.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 机器人列表项。
 * <p>
 * 名字 / 等级 / 职业来自 {@code characters} 表（机器人是真实角色），
 * {@code online} 来自运行时的内存表，不落库。
 *
 * @author WorkBuddy
 * @since 2026-10-17
 */
@Data
public class BotProfileDTO implements Serializable {

    private Integer id;
    private Integer characterId;
    private Integer accountId;

    /**
     * 角色名（characters.name）
     */
    private String name;

    /**
     * 等级（characters.level）
     */
    private Integer level;

    /**
     * 职业 id（characters.job）
     */
    private Integer job;

    /**
     * 性别 0=男 1=女（characters.gender）
     */
    private Integer gender;

    private Integer mapId;
    private Integer spawnX;
    private Integer spawnY;
    private Boolean autoStart;
    private String botType;
    private String note;

    /**
     * 是否在线（运行时状态）
     */
    private Boolean online;

    /**
     * 外观与装备是否已生成并落库；0 表示下次上线会随机生成
     */
    private Boolean decorated;

    /**
     * 人设档 S/A/B/C/D
     */
    private String botTier;

    private Date createTime;
    private Date updateTime;
}
