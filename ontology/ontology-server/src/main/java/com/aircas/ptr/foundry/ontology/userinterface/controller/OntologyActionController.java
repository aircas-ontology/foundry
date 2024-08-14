package com.aircas.ptr.foundry.ontology.userinterface.controller;

import com.aircas.ptr.foundry.common.base.ApiResult;
import com.aircas.ptr.foundry.common.base.DataResult;

import com.aircas.ptr.foundry.ontology.Exception.BaseException;
import com.aircas.ptr.foundry.ontology.Exception.OntologyFunctionParameterPropertyTypeNotSameException;
import com.aircas.ptr.foundry.ontology.application.service.OntologyActionService;
import com.aircas.ptr.foundry.ontology.entity.bo.ActionRequestBodyBO;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyActionBo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;


@Api(tags = "行为")
@RestController
@RequestMapping("/action")
public class OntologyActionController {

    @Resource
    OntologyActionService ontologyActionService;

    @ApiOperation(value = "新增行为")
    @PostMapping("/meta")
    public ApiResult update(@RequestBody OntologyActionBo ontologyFunctionBo) throws OntologyFunctionParameterPropertyTypeNotSameException {
        try {
            return DataResult.ofData(ontologyActionService.save(ontologyFunctionBo));
        } catch (BaseException e) {
            return DataResult.fail(e.getMessage(), e.code, e.getRootCauseMessage());
        }
    }

    @ApiOperation(value = "依据id删除行为")
    @DeleteMapping("/meta/{id}")
    public DataResult<Integer> delete(@PathVariable Long id) {
        return DataResult.ofData(ontologyActionService.delete(id));
    }

    //读取函数列表
    @ApiOperation(value = "根据api获取行为元数据")
    @GetMapping("/meta/{apiName}")
    public ApiResult getMetadataByApi(@PathVariable String apiName) {
        try {
            return DataResult.ofData(ontologyActionService.getMetadataByApi(apiName));
        } catch (BaseException e) {
            return DataResult.fail(e.getMessage(), e.code, e.getRootCauseMessage());
        }
    }

    @ApiOperation(value = "行为参数列表")
    @GetMapping("/parameter/{apiName}")
    public ApiResult getParameterByApi(@PathVariable String apiName) {
        try {
            return DataResult.ofData(ontologyActionService.getParametersByApi(apiName));
        } catch (BaseException e) {
            return DataResult.fail(e.getMessage(), e.code, e.getRootCauseMessage());
        }
    }

    @ApiOperation(value = "执行当前函数")
    @PostMapping("/execute")
    public ApiResult execute(@RequestBody ActionRequestBodyBO actionRequestBodyBO) {
        try {
            return DataResult.ofData(ontologyActionService.handle(actionRequestBodyBO));
        } catch (BaseException e) {
            return DataResult.fail(e.getMessage(), e.code, e.getRootCauseMessage());
        }
    }

    @ApiOperation(value = "查询某个本体拥有的函数")
    @GetMapping("/by_ontology")
    public ApiResult queryByOntologyUniqueIdentifier(@RequestParam @ApiParam(value = "本体identifier", required = true) String ontologyUniqueIdentifier) {
        try {
            return DataResult.ofData(ontologyActionService.queryByOntologyUniqueIdentifier(ontologyUniqueIdentifier));
        } catch (BaseException e) {
            return DataResult.fail(e.getMessage(), e.code, e.getRootCauseMessage());
        }
    }
}
