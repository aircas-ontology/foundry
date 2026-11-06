package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.common.base.ResultCode;
import com.aircas.ptr.foundry.common.util.DownloadUtil;
import com.aircas.ptr.foundry.ontology.controller.validator.SpaceIdVerify;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyExportTypeEnum;
import com.aircas.ptr.foundry.ontology.model.param.OntologySpaceCanvasCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologySpaceCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologySpaceUpdateParam;
import com.aircas.ptr.foundry.ontology.model.vo.OntologySpaceCanvasCreateVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologySpaceStatisticVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologySpaceVO;
import com.aircas.ptr.foundry.ontology.model.param.OntologySubspaceCreateParam;
import com.aircas.ptr.foundry.ontology.model.vo.OntologySubspaceCreateVO;
import com.aircas.ptr.foundry.ontology.service.OntologySpaceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;

@Tag(name = "对象空间管理")
@RestController
@RequestMapping("/space")
@RequiredArgsConstructor
@Validated
public class OntologySpaceController {

    private final OntologySpaceService ontologySpaceService;

    private final ObjectMapper objectMapper;

    private static final Set<String> FIELD_LEVEL_CONFLICT_REASONS =
            Set.of("FIELD_CONFLICT", "DELETED_BY_OTHERS", "DELETE_CONFLICT");
    private static final Set<String> STATE_CONFLICT_REASONS =
            Set.of("NAME_CONFLICT", "PROPERTY_DELETE_FORBIDDEN");


    @GetMapping("/export")
    @Operation(summary = "导出对象空间（含分类树、全部对象 schema，可选是否含实例数据）")
    public void exportOntologySpace(@RequestParam(name = "spaceId") Integer spaceId,
                                    @RequestParam(name = "exportType", defaultValue = "INSTANCE")
                                    @Parameter(description = "导出类型：SCHEMA=仅结构, INSTANCE=含实例数据（默认）")
                                    OntologyExportTypeEnum exportType,
                                    HttpServletResponse response) throws Exception {
        var dto = ontologySpaceService.exportOntologySpace(spaceId, exportType);
        var apiName = dto.getOntologySpace() != null ? dto.getOntologySpace().getApiName() : null;
        var suffix = exportType == OntologyExportTypeEnum.SCHEMA ? "_schema" : "_space";
        var fileName = (apiName == null || apiName.isEmpty() ? "space_" + spaceId : apiName) + suffix + ".json";
        DownloadUtil.writeJsonAttachment(response, objectMapper, fileName, dto);
    }


    @PostMapping("/import")
    @Operation(summary = "对象空间导入创建")
    public RestResult<List<String>> importOntologySpace(@RequestParam(required = true, name = "file") MultipartFile file) {
        var failedOntology = ontologySpaceService.importOntologySpace(file);
        return CollectionUtils.isNotEmpty(failedOntology) ?
                new RestResult<>(ResultCode.ERROR, "对象批量导入失败", failedOntology) : RestResult.success();
    }


    @PostMapping("/import_to_space")
    @Operation(summary = "将空间数据导入到已存在的目标空间（覆盖同名对象，保留分类树结构）")
    public RestResult<List<String>> importOntologySpaceToTarget(
            @RequestParam(required = true, name = "spaceId") @Parameter(description = "目标空间 ID") Integer spaceId,
            @RequestParam(required = true, name = "file") MultipartFile file) {
        var failedOntology = ontologySpaceService.importOntologySpaceToTarget(spaceId, file);
        return CollectionUtils.isNotEmpty(failedOntology) ?
                new RestResult<>(ResultCode.ERROR, "对象导入失败", failedOntology) : RestResult.success();
    }


    @PostMapping
    @Operation(summary = "创建对象空间")
    public RestResult<Integer> createSpace(@RequestBody @Valid OntologySpaceCreateParam param) {
        var id = ontologySpaceService.createSpace(param);
        return RestResult.ofData(id);
    }

    @PostMapping("/canvas")
    @Operation(summary = "保存画布：与数据库做三段对比后落库")
    public RestResult<OntologySpaceCanvasCreateVO> createSpaceWithCanvasContent(@RequestBody @Valid OntologySpaceCanvasCreateParam param) {
        OntologySpaceCanvasCreateVO res;
        try {
            res = ontologySpaceService.createSpaceWithCanvasContent(param);
        } catch (DataIntegrityViolationException e) {
            // 极小概率：提交时撞上唯一索引（通常是别人同时改了名），转成冲突而不是 500
            var conflict = OntologySpaceCanvasCreateVO.CanvasConflict.builder()
                    .reason("NAME_CONFLICT")
                    .message("保存时与唯一约束冲突，可能有其他人同时修改了名称，请刷新后重试")
                    .build();
            res = OntologySpaceCanvasCreateVO.builder()
                    .spaceId(param.getSpaceId())
                    .committed(false)
                    .saved(OntologySpaceCanvasCreateVO.CanvasSaveSummary.builder().build())
                    .conflicts(List.of(conflict))
                    .canvas(param.getSpaceId() == null ? null
                            : ontologySpaceService.getSpaceCanvasContent(param.getSpaceId()))
                    .build();
            return new RestResult<>(ResultCode.DUPLICATION, "画布保存失败，本次未写入任何数据", res);
        }
        if (Boolean.FALSE.equals(res.getCommitted())) {
            // 结构冲突里，重名/删除主键这类是"与现状冲突"，其余是"请求本身不合法"
            boolean conflictWithState = res.getConflicts().stream()
                    .filter(c -> !FIELD_LEVEL_CONFLICT_REASONS.contains(c.getReason()))
                    .allMatch(c -> STATE_CONFLICT_REASONS.contains(c.getReason()));
            return new RestResult<>(conflictWithState ? ResultCode.DUPLICATION : ResultCode.PARAM_ERROR,
                    "画布保存失败，存在无法合并的冲突，本次未写入任何数据", res);
        }
        if (CollectionUtils.isNotEmpty(res.getConflicts())) {
            return new RestResult<>(ResultCode.DUPLICATION, "部分内容存在冲突，已保存可合并的修改", res);
        }
        return RestResult.ofData(res);
    }

    @GetMapping("/canvas")
    @Operation(summary = "查询画布内容：返回空间下的全部对象、对象属性、对象关系",
            description = "与 POST /space/canvas 请求体同一结构，前端渲染画布、编辑后可直接回传")
    public RestResult<OntologySpaceCanvasCreateParam> getSpaceCanvasContent(
            @RequestParam(required = true, name = "spaceId")
            @Parameter(description = "对象空间id")
            @SpaceIdVerify Integer spaceId) {
        var res = ontologySpaceService.getSpaceCanvasContent(spaceId);
        return RestResult.ofData(res);
    }

    @PostMapping("/subspace")
    @Operation(summary = "基于父空间创建子空间",
            description = "创建子空间")
    public RestResult<OntologySubspaceCreateVO> createSubspace(@RequestBody @Valid OntologySubspaceCreateParam param) {
        var res = ontologySpaceService.createSubspace(param);
        return RestResult.ofData(res);
    }

    @PutMapping
    @Operation(summary = "修改对象空间")
    public RestResult updateSpace(@RequestBody @Valid OntologySpaceUpdateParam param) {
        ontologySpaceService.updateSpace(param);
        return RestResult.success();
    }


    @GetMapping
    @Operation(summary = "查询对象空间列表")
    public RestResult<List<OntologySpaceVO>> querySpace(
            @RequestParam(required = false, defaultValue = "0")
            @Parameter(description = "类型：0=空间（默认），1=主题") Integer type) {
        List<OntologySpaceVO> res = ontologySpaceService.querySpace(type);
        return RestResult.ofData(res);
    }

    @GetMapping("/statistic")
    @Operation(summary = "查询对象空间资源统计")
    public RestResult<OntologySpaceStatisticVO> getStatistic(@RequestParam(required = true, name = "spaceId")
                                                             @Parameter(description = "对象空间id")
                                                             @SpaceIdVerify Integer spaceId) {
        OntologySpaceStatisticVO res = ontologySpaceService.getStatistic(spaceId);
        return RestResult.ofData(res);
    }

    @DeleteMapping("/{spaceId}")
    @Operation(summary = "删除对象空间")
    public RestResult deleteSpace(@PathVariable(required = true, name = "spaceId") Integer spaceId) {
        ontologySpaceService.deleteSpace(spaceId);
        return RestResult.success();
    }

}
