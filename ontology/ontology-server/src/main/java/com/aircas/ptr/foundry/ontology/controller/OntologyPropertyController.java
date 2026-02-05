package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyPropertyPrimaryCategoryEnum;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyBatchCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyVisibilityUpdateParam;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyPrimaryCategoryVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyVisibilityVO;
import com.aircas.ptr.foundry.ontology.service.OntologyPropertyService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.var;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Api(tags = "属性")
@RestController
@RequestMapping("/property")
@RequiredArgsConstructor
@Validated
public class OntologyPropertyController {

    private final OntologyPropertyService ontologyPropertyService;


    @PostMapping("")
    @ApiOperation(value = "新增单个属性")
    public RestResult createProperty(@RequestBody @Valid OntologyPropertyCreateParam propertyCreateParam) {
        ontologyPropertyService.createProperty(propertyCreateParam);
        return RestResult.success();
    }


    @PostMapping("/batch")
    @ApiOperation(value = "批量新增属性")
    public RestResult batchCreateProperties(@RequestBody @Valid OntologyPropertyBatchCreateParam param) {
        ontologyPropertyService.batchCreateProperties(param.getProperties());
        return RestResult.success();
    }


    @PutMapping("")
    @ApiOperation(value = "单个更新本体属性")
    public RestResult updateProperty(@RequestBody @Valid OntologyPropertyUpdateParam propertyUpdateParam) {
        ontologyPropertyService.updateProperty(propertyUpdateParam);
        return RestResult.success();
    }

    @PutMapping("/batch")
    @ApiOperation(value = "批量更新本体属性")
    public RestResult batchUpdateProperties(@RequestBody @Valid List<OntologyPropertyUpdateParam> params) {
        ontologyPropertyService.batchUpdateProperties(params);
        return RestResult.success();
    }


    @DeleteMapping("/{propertyUniqueIdentifier}")
    @ApiOperation(value = "删除本体属性")
    public RestResult deleteProperty(@PathVariable(name = "propertyUniqueIdentifier", required = true) String propertyUniqueIdentifier) {
        ontologyPropertyService.deleteProperty(propertyUniqueIdentifier);
        return RestResult.success();
    }


    @GetMapping("/info")
    @ApiOperation(value = "根据本体identifier查询本体属性列表(用于展示)")
    public RestResult<List<OntologyPropertyInfoVO>> getPropertyInfoByOntologyId(@RequestParam(required = true, name = "ontologyUniqueIdentifier") @ApiParam(value = "本体uniqueIdentifier", required = true) @OntologyIdVerify String ontologyUniqueIdentifier) {
        return RestResult.ofData(ontologyPropertyService.getPropertyInfoByOntologyId(ontologyUniqueIdentifier));
    }

    @GetMapping("/detail")
    @ApiOperation(value = "根据本体identifier查询本体属性详细（用于编辑属性）")
    public RestResult<List<OntologyPropertyDetailVO>> getPropertyDetailByOntologyId(@RequestParam(required = true, name = "ontologyUniqueIdentifier") @ApiParam(value = "本体uniqueIdentifier", required = true) @OntologyIdVerify String ontologyUniqueIdentifier) {
        return RestResult.ofData(ontologyPropertyService.getPropertyDetailByOntologyId(ontologyUniqueIdentifier));
    }


    @GetMapping("")
    @ApiOperation(value = "根据uniqid查询属性")
    public RestResult<OntologyPropertyDetailVO> getPropertyByUniqueIdentifier(@RequestParam(required = true, name = "uniqueIdentifier") @ApiParam(value = "uniqueIdentifier", required = true) String uniqueIdentifier) {
        return RestResult.ofData(ontologyPropertyService.getPropertyDetailById(uniqueIdentifier));
    }

    @PostMapping("/query_list")
    @ApiOperation(value = "根据uniqid查询属性列表")
    public RestResult<List<OntologyPropertyDetailVO>> getPropertiesByUniqueIdentifier(@RequestBody @Valid List<String> uniqueIdentifiers) {
        return RestResult.ofData(ontologyPropertyService.getPropertiesDetailById(uniqueIdentifiers));
    }


    @PostMapping("/visibility")
    @ApiOperation(value = "保存属性可见性")
    public RestResult updatePropertyVisibility(@RequestBody @Valid OntologyPropertyVisibilityUpdateParam param) {
        ontologyPropertyService.updatePropertyVisibility(param);
        return RestResult.success();
    }


    @GetMapping("/visibility")
    @ApiOperation(value = "查询属性可见性")
    public RestResult<List<OntologyPropertyVisibilityVO>> getPropertyVisibility(@RequestParam(required = true, name = "ontologyUniqueIdentifier")
                                                                                @ApiParam(value = "本体uniqueIdentifier", required = true)
                                                                                @OntologyIdVerify String ontologyUniqueIdentifier) {
        var res = ontologyPropertyService.getPropertyVisibility(ontologyUniqueIdentifier);
        return RestResult.ofData(res);
    }


    @GetMapping("/primaryCategory")
    @ApiOperation(value = "属性一级分类列表")
    public RestResult<List<OntologyPropertyPrimaryCategoryVO>> listPropertyPrimaryCategory() {
        var list = Arrays.stream(OntologyPropertyPrimaryCategoryEnum.values())
                .<OntologyPropertyPrimaryCategoryVO>map(v -> OntologyPropertyPrimaryCategoryVO.builder()
                        .displayName(v.getName())
                        .key(v.toString())
                        .value(v.getValue())
                        .build())
                .collect(Collectors.toList());
        return RestResult.ofData(list);
    }

}
