package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.common.base.ResultCode;
import com.aircas.ptr.foundry.common.util.DownloadUtil;
import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyExportTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyOrderByEnum;
import com.aircas.ptr.foundry.ontology.model.enums.QuerySortEnum;
import com.aircas.ptr.foundry.ontology.model.param.OntologyAssetBuildParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyBatchDeleteParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyMetaCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyUpdateParam;
import com.aircas.ptr.foundry.ontology.model.vo.IdentifierVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyGroupMetaVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyImportResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaBriefVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaNodeVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaStatisticVO;
import com.aircas.ptr.foundry.ontology.service.OntologyMetaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;


@Tag(name = "对象管理")
@RestController
@RequestMapping("/meta")
@Validated
public class OntologyMetaController {

    @Resource
    private OntologyMetaService ontologyMetaService;

    @Resource
    private ObjectMapper jacksonObjectMapper;


    @GetMapping("/export")
    @Operation(summary = "导出对象（含 schema，可选是否含实例数据），支持多个对象")
    public void exportOntology(@RequestParam(name = "uniqueIdentifier")
                               @Parameter(description = "对象uniqueIdentifier，多个用逗号分隔") String uniqueIdentifier,
                               @RequestParam(name = "exportType", defaultValue = "INSTANCE")
                               @Parameter(description = "导出类型：SCHEMA=仅结构, INSTANCE=含实例数据（默认）")
                               OntologyExportTypeEnum exportType,
                               HttpServletResponse response) throws Exception {
        var dtos = ontologyMetaService.exportOntology(uniqueIdentifier, exportType);
        var apiName = dtos.size() == 1 && dtos.get(0).getMetadata() != null
                ? dtos.get(0).getMetadata().getApiName() : null;
        String fileName;
        if (dtos.size() > 1) {
            fileName = "ontologies_" + dtos.size();
        } else {
            fileName = apiName == null || apiName.isEmpty() ? "ontology_" + uniqueIdentifier : apiName;
        }
        var suffix = exportType == OntologyExportTypeEnum.SCHEMA ? "_schema" : "";
        DownloadUtil.writeJsonAttachment(response, jacksonObjectMapper, fileName + suffix + ".json", dtos);
    }


    @PostMapping("/import")
    @Operation(summary = "对象批量导入创建，目标空间重名时自动加 _1 后缀另建新对象")
    public RestResult importOntologies(
            @RequestParam(required = false, name = "spaceId")
            @Parameter(description = "目标空间 ID，不传则按 JSON 中的 ontologySpaceName 落位") Integer spaceId,
            @RequestParam(required = true, name = "file") MultipartFile file) {
        var result = ontologyMetaService.importOntologies(file, spaceId);
        var message = buildImportMessage(result);
        return CollectionUtils.isNotEmpty(result.getFailedOntologies())
                ? new RestResult(ResultCode.ERROR, message, result)
                : new RestResult(ResultCode.SUCCESS, message, result);
    }


    @PostMapping("/import_to_space")
    @Operation(summary = "导入对象到指定空间（不验证 displayName 唯一性，自动使用目标空间 ID）")
    public RestResult importOntologiesToSpace(
            @RequestParam(required = true, name = "spaceId") @Parameter(description = "目标空间 ID") Integer spaceId,
            @RequestParam(required = true, name = "file") MultipartFile file) {
        var failedOntology = ontologyMetaService.importOntologiesToSpace(spaceId, file);
        return CollectionUtils.isNotEmpty(failedOntology) ? new RestResult(ResultCode.ERROR, "对象导入失败", failedOntology) : RestResult.success();
    }


    @PostMapping
    @Operation(summary = "创建对象")
    public RestResult<IdentifierVO> createOntology(@RequestBody @Valid OntologyMetaCreateParam ontologyCreateParam) {
        String uniqIdentifier = ontologyMetaService.createOntology(ontologyCreateParam);
        return RestResult.ofData(IdentifierVO.builder().uniqueIdentifier(uniqIdentifier).build());
    }


    @Operation(summary = "资产构建对象")
    @PostMapping("/build_object")
    public RestResult<IdentifierVO> buildObjectWithProperties(@RequestBody @Valid OntologyAssetBuildParam param) {
        String uniqIdentifier = ontologyMetaService.createOntologyWithProperties(param);
        return RestResult.ofData(IdentifierVO.builder().uniqueIdentifier(uniqIdentifier).build());
    }


    @DeleteMapping("/{ontologyIdentifier}")
    @Operation(summary = "删除对象")
    public RestResult deleteOntology(@PathVariable(required = true, name = "ontologyIdentifier") @OntologyIdVerify String ontologyIdentifier) {
        ontologyMetaService.deleteOntology(ontologyIdentifier);
        return RestResult.success();
    }

    @DeleteMapping("/batch")
    @Operation(summary = "批量删除对象及其关联数据（属性、关系、分类树、行为等）")
    public RestResult batchDeleteOntologies(@RequestBody @Valid OntologyBatchDeleteParam param) {
        ontologyMetaService.batchDeleteOntologies(param);
        return RestResult.success();
    }

    @PutMapping
    @Operation(summary = "修改对象元数据")
    public RestResult updateMeta(@RequestBody @Valid OntologyUpdateParam updateParam) {
        ontologyMetaService.updateMeta(updateParam);
        return RestResult.success();
    }


    @GetMapping
    @Operation(summary = "根据unique identifier查询一个对象元数据")
    public RestResult<OntologyMetaInfoVO> getMetaByUniqueIdentifier(@RequestParam(name = "uniqueIdentifier", required = true) @Parameter(description = "对象unique identifer") @OntologyIdVerify String uniqueIdentifier) {
        return RestResult.ofData(ontologyMetaService.getMetaByUniqueIdentifier(uniqueIdentifier));
    }


    @GetMapping("/{id}")
    @Operation(summary = "根据对象id查询对象简要信息（id、对象名称、空间名称、uniqueIdentifier）")
    public RestResult<OntologyMetaBriefVO> getMetaById(@PathVariable(name = "id", required = true) @Parameter(description = "本地对象id（ontology_meta 主键）") Long id) {
        return RestResult.ofData(ontologyMetaService.getMetaById(id));
    }


    @GetMapping("/statistic")
    @Operation(summary = "统计对象关联的核心资源数量（实例、属性、关系、行为）")
    public RestResult<OntologyMetaStatisticVO> getStatistic(@RequestParam(name = "uniqueIdentifier", required = true) @Parameter(description = "对象unique identifer") @OntologyIdVerify String uniqueIdentifier) {
        return RestResult.ofData(ontologyMetaService.getStatistic(uniqueIdentifier));
    }


    @GetMapping("/search")
    @Operation(summary = "搜索对象")
    public RestResult<List<OntologyMetaInfoVO>> searchByKeyword(
            @RequestParam(name = "keyword", required = false) @Parameter(description = "搜索关键词") String keyword,
            @RequestParam(name = "spaceId", required = false) @Parameter(description = "空间 ID，用于过滤") Integer spaceId) {
        return RestResult.ofData(ontologyMetaService.searchByKeyword(keyword, spaceId));
    }

    @GetMapping("/category")
    @Operation(summary = "根据categoryId查询对象列表")
    public RestResult<List<OntologyMetaInfoVO>> getByCategoryId(@RequestParam(name = "categoryId", required = false) @Parameter(description = "分类id，不传则查询全部对象") Integer categoryId) {
        return RestResult.ofData(ontologyMetaService.getByCategoryId(categoryId));
    }

    @GetMapping("/group")
    @Operation(summary = "根据groupId查询组内对象")
    public RestResult<List<OntologyGroupMetaVO>> getByGroupId(@RequestParam(name = "groupId", required = false) @Parameter(description = "groupId") String groupId,
                                                              @RequestParam(name = "orderBy", required = false) @Parameter(description = "orderBy") OntologyOrderByEnum orderBy,
                                                              @RequestParam(name = "sort", required = false) @Parameter(description = "sort") QuerySortEnum sort) {
        return RestResult.ofData(ontologyMetaService.getByGroupId(groupId, orderBy, sort));
    }


    @GetMapping("/group/tree")
    @Operation(summary = "根据groupId查询组内对象树")
    public RestResult<List<OntologyMetaNodeVO>> getOntologyTreeByByGroupId(@RequestParam(name = "groupId", required = false) @Parameter(description = "groupId") String groupId) {
        return RestResult.ofData(ontologyMetaService.getOntologyTreeByByGroupId(groupId));
    }

    @GetMapping("/inheritance_tree")
    @Operation(summary = "查询空间内对象继承层级树")
    public RestResult<List<OntologyMetaNodeVO>> getInheritanceTree(@RequestParam(name = "spaceId", required = true) @Parameter(description = "对象空间id") @com.aircas.ptr.foundry.ontology.controller.validator.SpaceIdVerify Integer spaceId) {
        return RestResult.ofData(ontologyMetaService.getInheritanceTree(spaceId));
    }

    /**
     * 导入结果提示文案，前端直接展示：导入数量 + 逐项改名明细 + 失败名单。
     */
    private String buildImportMessage(OntologyImportResultVO result) {
        var message = new StringBuilder("成功导入对象")
                .append(result.getImportedOntologyCount()).append("个、关系")
                .append(result.getImportedRelationCount()).append("个");
        if (result.getSkippedRelationCount() > 0) {
            message.append("，跳过关系").append(result.getSkippedRelationCount()).append("个（端点对象不存在）");
        }
        var renamed = result.getRenamed();
        if (CollectionUtils.isNotEmpty(renamed)) {
            var ontologyDetail = formatRenamed(renamed, "ONTOLOGY");
            if (!ontologyDetail.isEmpty()) {
                message.append("；重命名对象：").append(ontologyDetail);
            }
            var relationDetail = formatRenamed(renamed, "RELATION");
            if (!relationDetail.isEmpty()) {
                message.append("；重命名关系：").append(relationDetail);
            }
        }
        if (CollectionUtils.isNotEmpty(result.getFailedOntologies())) {
            message.append("；导入失败对象：").append(String.join("、", result.getFailedOntologies()));
        }
        return message.toString();
    }

    private String formatRenamed(List<OntologyImportResultVO.RenamedItem> renamed, String kind) {
        return renamed.stream()
                .filter(item -> kind.equals(item.getKind()))
                .map(item -> {
                    var detail = item.getDisplayNameFrom() + "→" + item.getDisplayNameTo();
                    if (item.getApiNameTo() != null && !item.getApiNameTo().equals(item.getApiNameFrom())) {
                        detail = detail + "（apiName：" + item.getApiNameFrom() + "→" + item.getApiNameTo() + "）";
                    }
                    return detail;
                })
                .collect(Collectors.joining("、"));
    }

}
