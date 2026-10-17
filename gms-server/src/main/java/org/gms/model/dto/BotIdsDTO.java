package org.gms.model.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 机器人批量操作请求（上线 / 下线 / 删除）。
 *
 * @author WorkBuddy
 * @since 2026-10-17
 */
@Data
public class BotIdsDTO implements Serializable {

    /**
     * bot_profile.id 列表
     */
    private List<Integer> ids;
}
