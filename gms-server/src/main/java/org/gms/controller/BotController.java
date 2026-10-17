package org.gms.controller;

import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.gms.constants.api.ApiConstant;
import org.gms.dao.entity.BotEquipGenericPoolDO;
import org.gms.dao.entity.BotEquipNxPoolDO;
import org.gms.dao.entity.BotEquipOmitListDO;
import org.gms.model.dto.BotCreateDTO;
import org.gms.model.dto.BotEquipPoolDeleteDTO;
import org.gms.model.dto.BotIdsDTO;
import org.gms.model.dto.BotProfileDTO;
import org.gms.model.dto.BotUpdateDTO;
import org.gms.model.dto.ResultBody;
import org.gms.model.dto.SubmitBody;
import org.gms.service.BotEquipPoolService;
import org.gms.service.BotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 机器人管理接口。
 *
 * <p>契约与 gms-ui 的 axios 拦截器强耦合：
 * <ul>
 *   <li>带 body 的 POST 一律用 {@link SubmitBody} 接、{@link ResultBody#success(SubmitBody, Object)} 回；</li>
 *   <li><b>没有 body</b> 的 POST（{@code /equipPool/reload}）绝不能加 {@code @RequestBody}，
 *       否则前端不包装请求体会直接 400；</li>
 *   <li>GET 走 {@code @RequestParam}。</li>
 * </ul>
 *
 * @author WorkBuddy
 * @since 2026-10-17
 */
@RestController
@AllArgsConstructor
@RequestMapping("/bot")
public class BotController {

    private final BotService botService;
    private final BotEquipPoolService botEquipPoolService;

    // ---------------------------------------------------------------- 机器人档案

    @Tag(name = "/bot/" + ApiConstant.LATEST)
    @Operation(summary = "机器人列表（分页，可按角色名筛选）")
    @GetMapping("/" + ApiConstant.LATEST + "/list")
    public ResultBody<Page<BotProfileDTO>> list(@RequestParam(value = "page", required = false) Integer page,
                                                @RequestParam(value = "size", required = false) Integer size,
                                                @RequestParam(value = "name", required = false) String name) {
        return ResultBody.success(botService.list(page, size, name));
    }

    @Tag(name = "/bot/" + ApiConstant.LATEST)
    @Operation(summary = "新建机器人（建真实账号 + 真实角色）")
    @PostMapping("/" + ApiConstant.LATEST + "/create")
    public ResultBody<Object> create(@RequestBody SubmitBody<BotCreateDTO> request) {
        botService.create(request.getData());
        return ResultBody.success(request, null);
    }

    @Tag(name = "/bot/" + ApiConstant.LATEST)
    @Operation(summary = "修改机器人（等级/职业会同步写回 characters）")
    @PostMapping("/" + ApiConstant.LATEST + "/update")
    public ResultBody<Object> update(@RequestBody SubmitBody<BotUpdateDTO> request) {
        botService.update(request.getData());
        return ResultBody.success(request, null);
    }

    @Tag(name = "/bot/" + ApiConstant.LATEST)
    @Operation(summary = "批量上线")
    @PostMapping("/" + ApiConstant.LATEST + "/spawn")
    public ResultBody<Object> spawn(@RequestBody SubmitBody<BotIdsDTO> request) {
        botService.spawn(request.getData().getIds());
        return ResultBody.success(request, null);
    }

    @Tag(name = "/bot/" + ApiConstant.LATEST)
    @Operation(summary = "批量下线")
    @PostMapping("/" + ApiConstant.LATEST + "/despawn")
    public ResultBody<Object> despawn(@RequestBody SubmitBody<BotIdsDTO> request) {
        botService.despawn(request.getData().getIds());
        return ResultBody.success(request, null);
    }

    @Tag(name = "/bot/" + ApiConstant.LATEST)
    @Operation(summary = "批量删除（档案 + 真实角色一起删）")
    @PostMapping("/" + ApiConstant.LATEST + "/delete")
    public ResultBody<Object> delete(@RequestBody SubmitBody<BotIdsDTO> request) {
        botService.delete(request.getData().getIds());
        return ResultBody.success(request, null);
    }

    @Tag(name = "/bot/" + ApiConstant.LATEST)
    @Operation(summary = "批量重置装备（只换身上的装备，不动发型/脸/肤色）")
    @PostMapping("/" + ApiConstant.LATEST + "/resetEquip")
    public ResultBody<Object> resetEquip(@RequestBody SubmitBody<BotIdsDTO> request) {
        botService.resetEquip(request.getData().getIds());
        return ResultBody.success(request, null);
    }

    // ---------------------------------------------------------------- 装备随机池

    @Tag(name = "/bot/" + ApiConstant.LATEST)
    @Operation(summary = "通用装备池列表")
    @GetMapping("/" + ApiConstant.LATEST + "/equipPool/generic")
    public ResultBody<List<BotEquipGenericPoolDO>> listGeneric(@RequestParam(value = "category", required = false) String category) {
        return ResultBody.success(botEquipPoolService.listGenericPool(category));
    }

    @Tag(name = "/bot/" + ApiConstant.LATEST)
    @Operation(summary = "NX 点装池列表")
    @GetMapping("/" + ApiConstant.LATEST + "/equipPool/nx")
    public ResultBody<List<BotEquipNxPoolDO>> listNx(@RequestParam(value = "category", required = false) String category) {
        return ResultBody.success(botEquipPoolService.listNxPool(category));
    }

    @Tag(name = "/bot/" + ApiConstant.LATEST)
    @Operation(summary = "排除表列表")
    @GetMapping("/" + ApiConstant.LATEST + "/equipPool/omit")
    public ResultBody<List<BotEquipOmitListDO>> listOmit(@RequestParam(value = "blockType", required = false) String blockType) {
        return ResultBody.success(botEquipPoolService.listOmitList(blockType));
    }

    @Tag(name = "/bot/" + ApiConstant.LATEST)
    @Operation(summary = "保存通用装备池条目（id 为空=新增）")
    @PostMapping("/" + ApiConstant.LATEST + "/equipPool/generic/save")
    public ResultBody<Object> saveGeneric(@RequestBody SubmitBody<BotEquipGenericPoolDO> request) {
        botEquipPoolService.saveGenericPool(request.getData());
        botEquipPoolService.reloadCaches();
        return ResultBody.success(request, null);
    }

    @Tag(name = "/bot/" + ApiConstant.LATEST)
    @Operation(summary = "保存 NX 点装池条目（id 为空=新增）")
    @PostMapping("/" + ApiConstant.LATEST + "/equipPool/nx/save")
    public ResultBody<Object> saveNx(@RequestBody SubmitBody<BotEquipNxPoolDO> request) {
        botEquipPoolService.saveNxPool(request.getData());
        botEquipPoolService.reloadCaches();
        return ResultBody.success(request, null);
    }

    @Tag(name = "/bot/" + ApiConstant.LATEST)
    @Operation(summary = "保存排除表条目（id 为空=新增）")
    @PostMapping("/" + ApiConstant.LATEST + "/equipPool/omit/save")
    public ResultBody<Object> saveOmit(@RequestBody SubmitBody<BotEquipOmitListDO> request) {
        botEquipPoolService.saveOmitList(request.getData());
        botEquipPoolService.reloadCaches();
        return ResultBody.success(request, null);
    }

    @Tag(name = "/bot/" + ApiConstant.LATEST)
    @Operation(summary = "批量删除装备池条目")
    @PostMapping("/" + ApiConstant.LATEST + "/equipPool/delete")
    public ResultBody<Object> deletePoolRows(@RequestBody SubmitBody<BotEquipPoolDeleteDTO> request) {
        BotEquipPoolDeleteDTO data = request.getData();
        botEquipPoolService.deleteRows(data.getPoolType(), data.getIds());
        botEquipPoolService.reloadCaches();
        return ResultBody.success(request, null);
    }

    /**
     * ⚠️ 没有请求体，绝对不能加 {@code @RequestBody}。
     */
    @Tag(name = "/bot/" + ApiConstant.LATEST)
    @Operation(summary = "让三个装备池立即重新读库")
    @PostMapping("/" + ApiConstant.LATEST + "/equipPool/reload")
    public ResultBody<Object> reloadPools() {
        botEquipPoolService.reloadCaches();
        return ResultBody.success();
    }
}
