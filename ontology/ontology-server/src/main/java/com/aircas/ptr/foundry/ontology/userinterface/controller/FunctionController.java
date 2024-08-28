package com.aircas.ptr.foundry.ontology.userinterface.controller;

import com.aircas.ptr.foundry.common.base.ApiResult;
import com.aircas.ptr.foundry.common.base.DataResult;
import com.aircas.ptr.foundry.ontology.Exception.BaseException;
import com.aircas.ptr.foundry.ontology.application.service.FunctionService;
import com.aircas.ptr.foundry.ontology.entity.bo.FunctionBo;
import com.aircas.ptr.foundry.ontology.entity.vo.FunctionVO;
import com.aircas.ptr.foundry.ontology.repository.param.FunctionAddParam;
import com.alibaba.fastjson.JSONObject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;


@Api(tags = "函数")
@RestController
@RequestMapping("/function")
public class FunctionController {

    @Resource
    FunctionService functionService;

    @ApiOperation(value = "执行某个function")
    @PostMapping("/execute")
    public ApiResult execute(@RequestBody HashMap map) {
        String functionName = (String) map.getOrDefault("functionName", null);
        String objectTypes = (String) map.getOrDefault("objectTypes", null);
        Boolean isPreview = (Boolean) map.getOrDefault("isPreview", true);
        HashMap<String, Object> parameters = (HashMap<String, Object>) map.getOrDefault("parameters", null);
        try {
            return DataResult.ofData(functionService.handle(functionName, false, objectTypes, parameters));
        } catch (BaseException e) {
            return DataResult.fail(e.getMessage(), e.code, e.getRootCauseMessage());
        }
    }

    @ApiOperation(value = "得到函数参数类型")
    @GetMapping("/parameter/{api}")
    public ApiResult queryParameter(@PathVariable String api) {
        try {
            return DataResult.ofData(functionService.getParameters(api));
        } catch (BaseException e) {
            return DataResult.fail(e.getMessage(), e.code, e.getRootCauseMessage());
        }
    }

    @ApiOperation(value = "保存函数Metadata")
    @PostMapping("/meta")
    public DataResult<Integer> saveFunctionMetadata(@RequestBody FunctionAddParam addParam) {

        FunctionBo functionBo = new FunctionBo();
        BeanUtils.copyProperties(addParam, functionBo);
        return DataResult.ofData(functionService.saveFunctionMetadata(functionBo));
    }

    @ApiOperation(value = "更新函数metadata")
    @PutMapping("/meta")
    public DataResult<Integer> updateFunctionMetadata(@RequestBody FunctionBo functionBo) {
        return DataResult.ofData(functionService.updateFunctionMetadata(functionBo));
    }

    @ApiOperation(value = "根据api获取函数metadata")
    @GetMapping("/meta/by_api/{api}")
    public DataResult<FunctionVO> getMetadataByApi(@PathVariable String api) {
        return DataResult.ofData(functionService.getFunctionByApi(api));
    }

    @ApiOperation(value = "根据函数id获取函数的元数据")
    @GetMapping("/meta/by_id/{id}")
    public DataResult<FunctionVO> getFunctionById(@PathVariable Long id) {

        return DataResult.ofData(functionService.queryById(id));
    }

    @ApiOperation(value = "删除函数")
    @DeleteMapping("/meta/by_api/{api}")
    public DataResult<Boolean> delete(@PathVariable String api) {

        return DataResult.ofData(functionService.deleteByApi(api));
    }

    @ApiOperation(value = "根据id删除函数")
    @DeleteMapping("/meta/by_id/{id}")
    public DataResult<Boolean> deleteById(@PathVariable Long id) {

        return DataResult.ofData(functionService.deleteById(id));
    }

    @ApiOperation(value = "读取函数列表")
    @GetMapping("/meta/list")
    public DataResult<List<FunctionVO>> functionList() {
        return DataResult.ofData(functionService.functionMetadataList());
    }

    @ApiOperation(value = "保存代码")
    @PostMapping("/code")
    public DataResult<Boolean> saveCode(@RequestBody HashMap map) {

        String functionName = (String) map.getOrDefault("functionName", null);
        if (functionName == null) {
            return DataResult.ofData(false);
        }
        String code = (String) map.getOrDefault("code", null);
        if (code == null) {
            return DataResult.ofData(false);
        }
        Boolean isPreview = (Boolean) map.getOrDefault("isPreview", true);
        return DataResult.ofData(functionService.write(functionName, code, isPreview));
    }

    @ApiOperation(value = "获取函数代码")
    @GetMapping("/code/{api}")
    public DataResult<String> getCode(@PathVariable String api) {

        return DataResult.ofData(functionService.get(api,false));
    }



}
