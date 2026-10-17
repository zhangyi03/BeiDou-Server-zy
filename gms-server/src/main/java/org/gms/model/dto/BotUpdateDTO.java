package org.gms.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 修改机器人请求。
 * <p>
 * 只带 id 的字段保持原值（{@code null} 表示不改）。等级 / 职业一旦改动会同步写回
 * {@code characters} 表，所以这里属于「改真实角色」，不是只改档案。
 *
 * @author WorkBuddy
 * @since 2026-10-17
 */
@Data
public class BotUpdateDTO implements Serializable {

    private Integer id;

    private Integer level;

    private Integer job;

    private Integer mapId;

    private Integer spawnX;

    private Integer spawnY;

    private Boolean autoStart;

    /**
     * 机器人行为类型码，{@code null} 表示不改；取值见 {@code BotType}
     */
    private String botType;

    private String note;
}
