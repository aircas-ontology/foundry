package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.common.base.ResultCode;
import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyOrderByEnum;
import com.aircas.ptr.foundry.ontology.model.enums.QuerySortEnum;
import com.aircas.ptr.foundry.ontology.model.param.OntologyMetaCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyUpdateParam;
import com.aircas.ptr.foundry.ontology.model.vo.IdentifierVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyGroupMetaVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaNodeVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaStatisticVO;
import com.aircas.ptr.foundry.ontology.service.OntologyMetaService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;


@Tag(name = "本体对象管理")
@RestController
@RequestMapping("/meta")
@Validated
public class OntologyMetaController {

    @Resource
    private OntologyMetaService ontologyMetaService;


    @PostMapping("/import")
    @Operation(summary = "本体批量导入创建")
    public RestResult importOntologies(@RequestParam(required = true, name = "file") MultipartFile file) {
        var failedOntology = ontologyMetaService.importOntologies(file);
        return CollectionUtils.isNotEmpty(failedOntology) ? new RestResult(ResultCode.ERROR, "本体批量导入失败", failedOntology) : RestResult.success();
    }


    @PostMapping
    @Operation(summary = "创建本体")
    public RestResult<IdentifierVO> createOntology(@RequestBody @Valid OntologyMetaCreateParam ontologyCreateParam) {
        String uniqIdentifier = ontologyMetaService.createOntology(ontologyCreateParam);
        return RestResult.ofData(IdentifierVO.builder().uniqueIdentifier(uniqIdentifier).build());
    }


    @DeleteMapping("/{ontologyIdentifier}")
    @Operation(summary = "删除本体")
    public RestResult deleteOntology(@PathVariable(required = true, name = "ontologyIdentifier") @OntologyIdVerify String ontologyIdentifier) {
        ontologyMetaService.deleteOntology(ontologyIdentifier);
        return RestResult.success();
    }

    @PutMapping
    @Operation(summary = "修改本体元数据")
    public RestResult updateMeta(@RequestBody @Valid OntologyUpdateParam updateParam) {
        ontologyMetaService.updateMeta(updateParam);
        return RestResult.success();
    }


    @GetMapping
    @Operation(summary = "根据unique identifier查询一个本体元数据")
    public RestResult<OntologyMetaInfoVO> getMetaByUniqueIdentifier(@RequestParam(name = "uniqueIdentifier", required = true) @Parameter(description = "本体unique identifer") @OntologyIdVerify String uniqueIdentifier) {
        return RestResult.ofData(ontologyMetaService.getMetaByUniqueIdentifier(uniqueIdentifier));
    }


    @GetMapping("/statistic")
    @Operation(summary = "统计本体对象关联的核心资源数量（实例、属性、关系、行为）")
    public RestResult<OntologyMetaStatisticVO> getStatistic(@RequestParam(name = "uniqueIdentifier", required = true) @Parameter(description = "本体unique identifer") @OntologyIdVerify String uniqueIdentifier) {
        return RestResult.ofData(ontologyMetaService.getStatistic(uniqueIdentifier));
    }


    @GetMapping("/search")
    @Operation(summary = "搜索本体")
    public RestResult<List<OntologyMetaInfoVO>> searchByKeyword(@RequestParam(name = "keyword", required = false) @Parameter(description = "搜索关键词") String keyword) {
        return RestResult.ofData(ontologyMetaService.searchByKeyword(keyword));
    }

    @GetMapping("/category")
    @Operation(summary = "根据categoryId查询本体对象列表")
    public RestResult<List<OntologyMetaInfoVO>> getByCategoryId(@RequestParam(name = "categoryId", required = false) @Parameter(description = "分类id，不传则查询全部本体") Integer categoryId) {
        return RestResult.ofData(ontologyMetaService.getByCategoryId(categoryId));
    }

    @GetMapping("/group")
    @Operation(summary = "根据groupId查询组内本体")
    public RestResult<List<OntologyGroupMetaVO>> getByGroupId(@RequestParam(name = "groupId", required = false) @Parameter(description = "groupId") String groupId,
                                                              @RequestParam(name = "orderBy", required = false) @Parameter(description = "orderBy") OntologyOrderByEnum orderBy,
                                                              @RequestParam(name = "sort", required = false) @Parameter(description = "sort") QuerySortEnum sort) {
        return RestResult.ofData(ontologyMetaService.getByGroupId(groupId, orderBy, sort));
    }


    @GetMapping("/group/tree")
    @Operation(summary = "根据groupId查询组内本体树")
    public RestResult<List<OntologyMetaNodeVO>> getOntologyTreeByByGroupId(@RequestParam(name = "groupId", required = false) @Parameter(description = "groupId") String groupId) {
        return RestResult.ofData(ontologyMetaService.getOntologyTreeByByGroupId(groupId));
    }

}
