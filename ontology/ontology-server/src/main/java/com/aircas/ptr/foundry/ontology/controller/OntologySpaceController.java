package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.common.base.ResultCode;
import com.aircas.ptr.foundry.common.util.DownloadUtil;
import com.aircas.ptr.foundry.ontology.controller.validator.SpaceIdVerify;
import com.aircas.ptr.foundry.ontology.model.param.OntologySpaceCanvasCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologySpaceCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologySpaceUpdateParam;
import com.aircas.ptr.foundry.ontology.model.vo.OntologySpaceCanvasCreateVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologySpaceStatisticVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologySpaceVO;
import com.aircas.ptr.foundry.ontology.service.OntologySpaceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;

import jakarta.validation.Valid;
import java.util.List;

@Tag(name = "本体空间管理")
@RestController
@RequestMapping("/space")
@RequiredArgsConstructor
@Validated
public class OntologySpaceController {

    private final OntologySpaceService ontologySpaceService;

    private final ObjectMapper objectMapper;


    @GetMapping("/export")
    @Operation(summary = "导出本体空间（含分类树、全部本体 schema 与实例数据）")
    public void exportOntologySpace(@RequestParam(name = "spaceId") Integer spaceId,
                                    HttpServletResponse response) throws Exception {
        var dto = ontologySpaceService.exportOntologySpace(spaceId);
        var apiName = dto.getOntologySpace() != null ? dto.getOntologySpace().getApiName() : null;
        var fileName = (apiName == null || apiName.isEmpty() ? "space_" + spaceId : apiName) + "_space.json";
        DownloadUtil.writeJsonAttachment(response, objectMapper, fileName, dto);
    }


    @PostMapping("/import")
    @Operation(summary = "本体空间导入创建")
    public RestResult<List<String>> importOntologySpace(@RequestParam(required = true, name = "file") MultipartFile file) {
        var failedOntology = ontologySpaceService.importOntologySpace(file);
        return CollectionUtils.isNotEmpty(failedOntology) ?
                new RestResult<>(ResultCode.ERROR, "本体批量导入失败", failedOntology) : RestResult.success();
    }


    @PostMapping
    @Operation(summary = "创建本体空间")
    public RestResult<Integer> createSpace(@RequestBody @Valid OntologySpaceCreateParam param) {
        var id = ontologySpaceService.createSpace(param);
        return RestResult.ofData(id);
    }

    @PostMapping("/canvas")
    @Operation(summary = "画布一键建空间：创建空间并批量创建对象、属性、关系")
    public RestResult<OntologySpaceCanvasCreateVO> createSpaceWithCanvasContent(@RequestBody @Valid OntologySpaceCanvasCreateParam param) {
        var res = ontologySpaceService.createSpaceWithCanvasContent(param);
        return RestResult.ofData(res);
    }

    @PutMapping
    @Operation(summary = "修改本体空间")
    public RestResult updateSpace(@RequestBody @Valid OntologySpaceUpdateParam param) {
        ontologySpaceService.updateSpace(param);
        return RestResult.success();
    }


    @GetMapping
    @Operation(summary = "查询本体空间列表")
    public RestResult<List<OntologySpaceVO>> querySpace() {
        List<OntologySpaceVO> res = ontologySpaceService.querySpace();
        return RestResult.ofData(res);
    }

    @GetMapping("/statistic")
    @Operation(summary = "查询本体空间资源统计")
    public RestResult<OntologySpaceStatisticVO> getStatistic(@RequestParam(required = true, name = "spaceId")
                                                             @Parameter(description = "本体空间id")
                                                             @SpaceIdVerify Integer spaceId) {
        OntologySpaceStatisticVO res = ontologySpaceService.getStatistic(spaceId);
        return RestResult.ofData(res);
    }

    @DeleteMapping("/{spaceId}")
    @Operation(summary = "删除本体空间")
    public RestResult deleteSpace(@PathVariable(required = true, name = "spaceId") Integer spaceId) {
        ontologySpaceService.deleteSpace(spaceId);
        return RestResult.success();
    }

}
