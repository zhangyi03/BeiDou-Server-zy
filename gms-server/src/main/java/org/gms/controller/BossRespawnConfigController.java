package org.gms.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.gms.constants.api.ApiConstant;
import org.gms.model.dto.BossRespawnConfigDTO;
import org.gms.model.dto.ResultBody;
import org.gms.model.dto.SubmitBody;
import org.gms.service.BossRespawnConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 野外Boss刷新配置控制器。
 * <p>
 * 改完配置需要在游戏内执行 {@code !reloadevents} 让新间隔立即生效，
 * 已提交的定时器不会自动采用新值（最多等满一个旧周期）。
 *
 * @author WorkBuddy
 * @since 2026-09-23
 */
@RestController
@AllArgsConstructor
@RequestMapping("/bossrespawn")
public class BossRespawnConfigController {
    private final BossRespawnConfigService bossRespawnConfigService;

    @Tag(name = "/bossrespawn/" + ApiConstant.LATEST)
    @Operation(summary = "获取野外Boss刷新配置列表")
    @GetMapping("/" + ApiConstant.LATEST + "/getConfigList")
    public ResultBody<List<BossRespawnConfigDTO>> getConfigList() {
        return ResultBody.success(bossRespawnConfigService.getConfigList());
    }

    @Tag(name = "/bossrespawn/" + ApiConstant.LATEST)
    @Operation(summary = "更新野外Boss刷新配置")
    @PostMapping("/" + ApiConstant.LATEST + "/updateConfig")
    public ResultBody<Object> updateConfig(@RequestBody SubmitBody<BossRespawnConfigDTO> request) {
        bossRespawnConfigService.updateConfig(request.getData());
        return ResultBody.success(request, null);
    }

    @Tag(name = "/bossrespawn/" + ApiConstant.LATEST)
    @Operation(summary = "删除野外Boss刷新配置（回落脚本默认值）")
    @PostMapping("/" + ApiConstant.LATEST + "/deleteConfig")
    public ResultBody<Object> deleteConfig(@RequestBody SubmitBody<BossRespawnConfigDTO> request) {
        bossRespawnConfigService.deleteConfig(request.getData().getEventName());
        return ResultBody.success(request, null);
    }

    @Tag(name = "/bossrespawn/" + ApiConstant.LATEST)
    @Operation(summary = "重载野外Boss刷新配置并重建排期（立即生效）")
    @PostMapping("/" + ApiConstant.LATEST + "/reload")
    public ResultBody<Object> reload() {
        bossRespawnConfigService.reloadAll();
        return ResultBody.success();
    }
}
