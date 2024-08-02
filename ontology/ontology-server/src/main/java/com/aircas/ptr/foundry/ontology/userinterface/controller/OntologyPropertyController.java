package com.aircas.ptr.foundry.ontology.userinterface.controller;

import com.aircas.ptr.foundry.common.base.DataResult;
import com.aircas.ptr.foundry.ontology.application.service.OntologyPropertyService;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyPropertyBO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyPropertyVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Api(tags = "属性")
@RestController
@RequestMapping("/OntologyProperty")
@RequiredArgsConstructor
public class OntologyPropertyController {
    private final OntologyPropertyService ontologyPropertyService;

    @PostMapping("/add")
    @ApiOperation(value = "新增本体属性")
    public DataResult<Integer> add(@RequestBody OntologyPropertyBO ontologyPropertyBO) {
        return DataResult.ofData(ontologyPropertyService.add(ontologyPropertyBO));
    }

    @PostMapping("/batch_add")
    @ApiOperation(value = "新增多个本体属性")
    public DataResult<Integer> add(@RequestBody List<OntologyPropertyBO> ontologyPropertyBOs) {
        return DataResult.ofData(ontologyPropertyService.batchAdd(ontologyPropertyBOs));
    }

    @PostMapping("/batch_update")
    @ApiOperation(value = "更新多个本体属性")
    public DataResult<Integer> update(@RequestBody List<OntologyPropertyBO> ontologyPropertyBOs) {
        return DataResult.ofData(ontologyPropertyService.batchUpdate(ontologyPropertyBOs));
    }


    @PostMapping("/delete")
    @ApiOperation(value = "删除本体属性")
    public DataResult<Integer> delete(@RequestParam @ApiParam(value = "本体属性id", required = true) String uniqueIdentifier) {
        return DataResult.ofData(ontologyPropertyService.delete(uniqueIdentifier));
    }

    @PostMapping("/update")
    @ApiOperation(value = "修改本体属性")
    public DataResult<Integer> update(@RequestBody OntologyPropertyBO ontologyPropertyBO) {
        return DataResult.ofData(ontologyPropertyService.update(ontologyPropertyBO));
    }

    @GetMapping("/queryByOntologyUniqueIdentifier")
    @ApiOperation(value = "根据本体identifier查询本体属性列表")
    public DataResult<List<OntologyPropertyVO>> getOntologyById(@RequestParam @ApiParam(value = "本体uniqueIdentifier", required = true) String uniqueIdentifier) {
        return DataResult.ofData(ontologyPropertyService.selectByOntologyUniqueIdentifier(uniqueIdentifier));
    }
}
