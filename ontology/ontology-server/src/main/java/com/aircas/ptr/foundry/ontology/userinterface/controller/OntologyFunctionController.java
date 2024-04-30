package com.aircas.ptr.foundry.ontology.userinterface.controller;

import com.aircas.ptr.foundry.common.base.ApiResult;
import com.aircas.ptr.foundry.common.base.DataResult;

import com.aircas.ptr.foundry.ontology.Exception.BaseException;
import com.aircas.ptr.foundry.ontology.Exception.OntologyFunctionMappedPropertyNotFoundException;
import com.aircas.ptr.foundry.ontology.Exception.OntologyFunctionParameterPropertyTypeNotSameException;
import com.aircas.ptr.foundry.ontology.application.service.OntologyFunctionService;
import com.aircas.ptr.foundry.ontology.entity.bo.FunctionRequestBodyBO;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyFunctionBo;
import com.aircas.ptr.foundry.ontology.entity.vo.FunctionVO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyFunctionVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;


@Api(tags = "函数")
@RestController
@RequestMapping("/ontology_function")
public class OntologyFunctionController {

    @Resource
    OntologyFunctionService ontologyFunctionService;

    @ApiOperation(value = "保存当前函数")
    @PostMapping("/update")
    public ApiResult update(@RequestBody OntologyFunctionBo ontologyFunctionBo) throws OntologyFunctionParameterPropertyTypeNotSameException {
        try {
            return DataResult.ofData(ontologyFunctionService.save(ontologyFunctionBo));
        } catch (BaseException e) {
            return DataResult.fail(e.getMessage(), e.code, e.getRootCauseMessage());
        }
    }

    //读取函数列表
    @ApiOperation(value = "根据api获取函数metadata")
    @GetMapping("/getMetadata")
    public ApiResult getMetadata(@RequestParam String functionName, @RequestParam String ontologyUniqueIdentifier, @RequestParam Boolean isPreview) {
        try {
            return DataResult.ofData(ontologyFunctionService.getMetadata(functionName, ontologyUniqueIdentifier, isPreview));
        } catch (BaseException e) {
            return DataResult.fail(e.getMessage(), e.code, e.getRootCauseMessage());
        }
    }

    @ApiOperation(value = "执行当前函数")
    @PostMapping("/execute")
    public ApiResult execute(@RequestBody FunctionRequestBodyBO functionRequestBodyBO) {
        try {
            return DataResult.ofData(ontologyFunctionService.handle(functionRequestBodyBO));
        } catch (BaseException e) {
            return DataResult.fail(e.getMessage(), e.code, e.getRootCauseMessage());
        }
    }

    @ApiOperation(value = "参数列表")
    @GetMapping("/parameterMetadatas")
    public ApiResult getParameterMetadatas(@RequestParam String functionName, @RequestParam String ontologyUniqueIdentifier, @RequestParam Boolean isPreview) {
        try {
            return DataResult.ofData(ontologyFunctionService.getParameters(functionName, ontologyUniqueIdentifier, isPreview));
        } catch (BaseException e) {
            return DataResult.fail(e.getMessage(), e.code, e.getRootCauseMessage());
        }
    }

    @ApiOperation(value = "删除当前函数")
    @PostMapping("/delete")
    public DataResult<Integer> delete(@RequestBody  @ApiParam(value = "函数id", required = true) long id) {
        return DataResult.ofData(ontologyFunctionService.delete(id));
    }


    @ApiOperation(value = "查询某个本体拥有的函数")
    @GetMapping("/query")
    public ApiResult queryByOntologyUniqueIdentifier(@RequestParam @ApiParam(value = "本体identifier", required = true) String ontologyUniqueIdentifier) {
        try {
            return DataResult.ofData(ontologyFunctionService.queryByOntologyUniqueIdentifier(ontologyUniqueIdentifier));
        } catch (BaseException e) {
            return DataResult.fail(e.getMessage(), e.code, e.getRootCauseMessage());
        }
    }
}
