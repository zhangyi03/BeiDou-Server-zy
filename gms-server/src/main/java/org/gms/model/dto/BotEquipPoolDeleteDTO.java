package org.gms.model.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 装备池批量删除请求。
 *
 * @author WorkBuddy
 * @since 2026-10-17
 */
@Data
public class BotEquipPoolDeleteDTO implements Serializable {

    /**
     * GENERIC / NX / OMIT
     */
    private String poolType;

    private List<Integer> ids;
}
