package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.param.*;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyDatasourcePropertyVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyInfoVO;
import com.aircas.ptr.foundry.ontology.service.OntologyPropertyService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@Api(tags = "属性")
@RestController
@RequestMapping("/property")
@RequiredArgsConstructor
public class OntologyPropertyController {

    private final OntologyPropertyService ontologyPropertyService;

    @PostMapping("/create_datasource")
    @ApiOperation(value = "新增数据源")
    public RestResult createDatasource(@RequestBody @Valid OntologyDataSourceCreateParam dataSourceCreateParam) {
        return RestResult.success();
    }

    @PostMapping("")
    @ApiOperation(value = "新增属性")
    public RestResult createProperty(@RequestBody @Valid OntologyPropertyCreateParam propertyCreateParam) {
        return RestResult.success();
    }


    @PutMapping("")
    @ApiOperation(value = "更新本体属性")
    public RestResult updateProperty(@RequestBody @Valid List<OntologyPropertyUpdateParam> propertyUpdateParams) {
        return RestResult.success();
    }


    @DeleteMapping("/{propertyId}")
    @ApiOperation(value = "删除本体属性")
    public RestResult delete(@PathVariable(name = "propertyId",required = true) String propertyId) {
        return RestResult.success();
    }


    @DeleteMapping("/delete_datasource/{datasource}")
    @ApiOperation(value = "删除数据源下的所有属性")
    public RestResult deleteDatasource(@PathVariable(required = true,name = "datasource") String datasource) {
        return RestResult.success();
    }

    @GetMapping("/info/list")
    @ApiOperation(value = "根据本体identifier查询本体属性列表(用于展示)")
    public RestResult<List<OntologyPropertyInfoVO>> getPropertyInfoByOntologyId(@RequestParam(required = true, name = "ontologyUniqueIdentifier") @ApiParam(value = "本体uniqueIdentifier", required = true) String ontologyUniqueIdentifier) {
        return RestResult.ofData(null);
    }

    @GetMapping("/detail/list")
    @ApiOperation(value = "根据本体identifier查询本体属性详细（用于编辑属性）")
    public RestResult<OntologyDatasourcePropertyVO> getPropertyDetailByOntologyId(@RequestParam(required = true, name = "ontologyUniqueIdentifier") @ApiParam(value = "本体uniqueIdentifier", required = true) String ontologyUniqueIdentifier) {
        return RestResult.ofData(null);
    }


}
