package com.aircas.ptr.foundry.ontology.userinterface.controller;

import com.aircas.ptr.foundry.common.base.ApiResult;
import com.aircas.ptr.foundry.common.base.DataResult;
import com.aircas.ptr.foundry.ontology.Exception.BaseException;
import com.aircas.ptr.foundry.ontology.application.service.FunctionService;
import com.aircas.ptr.foundry.ontology.entity.bo.FunctionBo;
import com.aircas.ptr.foundry.ontology.entity.vo.FunctionVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;


@Api(tags = "function")
@RestController
@RequestMapping("/function")
public class FunctionHandler {

    @Resource
    FunctionService functionService;

    @ApiOperation(value = "执行某个function")
    @PostMapping("/execute")
    public ApiResult execute(@RequestBody HashMap map) {
        String functionName = (String) map.getOrDefault("functionName", null);
        String objectTypes = (String) map.getOrDefault("objectTypes", null);
        Boolean isPreview = (Boolean) map.getOrDefault("isPreview", true);
        HashMap<String, Object> parameters= (HashMap<String, Object>) map.getOrDefault("parameters", null);
        try {
            return DataResult.ofData(functionService.handle(functionName, isPreview, objectTypes, parameters));
        } catch (BaseException e) {
            return DataResult.fail(e.getMessage(), e.code, e.getRootCause().getMessage());
        }
    }

    @ApiOperation(value = "得到函数参数类型")
    @GetMapping("/parameterMetadatas")
    public ApiResult getParameterMetadatas(@RequestBody HashMap map) {
        String functionName = (String) map.getOrDefault("functionName", null);
        String objectTypes = (String) map.getOrDefault("objectTypes", null);
        Boolean isPreview = (Boolean) map.getOrDefault("isPreview", true);
        try {
            return DataResult.ofData(functionService.getParameters(functionName, isPreview, objectTypes));
        } catch (BaseException e) {
            return DataResult.fail(e.getMessage(), e.code, e.getRootCause().getMessage());
        }
    }

    // 将函数
    @ApiOperation(value = "保存函数")
    @PostMapping("/saveMetadata")
    public DataResult<Integer> saveFunctionMetadata(@RequestBody FunctionBo functionBo) {
        return DataResult.ofData(functionService.saveFunctionMetadata(functionBo));
    }

    //读取函数列表
    @ApiOperation(value = "读取函数列表")
    @GetMapping("/list")
    public DataResult<List<FunctionVO>> functionList() {
        return DataResult.ofData(functionService.functionMetadataList());
    }

    //读取函数列表
    @ApiOperation(value = "根据api获取函数metadata")
    @GetMapping("/getMetadataByApi")
    public DataResult<FunctionVO> functionList(String api) {
        return DataResult.ofData(functionService.getFunctionByApi(api));
    }

    @ApiOperation(value = "保存代码")
    @PostMapping("/write")
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

    @ApiOperation(value = "获取代码")
    @GetMapping("/get")
    public DataResult<String> getCode(@RequestParam String functionName, @RequestParam Boolean isPreview) {
        return DataResult.ofData(functionService.get(functionName, isPreview));
    }

    @ApiOperation(value = "删除函数")
    @PostMapping("/delete")
    public DataResult<Boolean> delete(@RequestParam String functionName) {
        return DataResult.ofData(functionService.delete(functionName));
    }

}
