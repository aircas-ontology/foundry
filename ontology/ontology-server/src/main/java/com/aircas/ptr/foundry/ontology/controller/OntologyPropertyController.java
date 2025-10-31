package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyCreateParamV2;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyUpdateParam;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyInfoVO;
import com.aircas.ptr.foundry.ontology.service.OntologyPropertyService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Api(tags = "属性")
@RestController
@RequestMapping("/property")
@RequiredArgsConstructor
@Validated
public class OntologyPropertyController {

    private final OntologyPropertyService ontologyPropertyService;

//    @PostMapping("/create_datasource")
//    @ApiOperation(value = "新增属性数据源")
//    public RestResult createDatasource(@RequestBody @Valid OntologyDataSourceCreateParam dataSourceCreateParam) {
//        ontologyPropertyService.createDatasource(dataSourceCreateParam);
//        return RestResult.success();
//    }

    @PostMapping("")
    @ApiOperation(value = "新增单个属性")
    public RestResult createProperty(@RequestBody @Valid OntologyPropertyCreateParamV2 propertyCreateParam) {
        ontologyPropertyService.createProperty(propertyCreateParam);
        return RestResult.success();
    }


    @PutMapping("")
    @ApiOperation(value = "单个更新本体属性")
    public RestResult updateProperty(@RequestBody @Valid OntologyPropertyUpdateParam propertyUpdateParam) {
        ontologyPropertyService.updateProperty(propertyUpdateParam);
        return RestResult.success();
    }


    @DeleteMapping("/{propertyUniqueIdentifier}")
    @ApiOperation(value = "删除本体属性")
    public RestResult delete(@PathVariable(name = "propertyUniqueIdentifier", required = true) String propertyUniqueIdentifier) {
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


}
