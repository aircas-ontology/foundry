package com.aircas.ptr.foundry.ontology.userinterface.controller;

import com.aircas.ptr.foundry.common.base.DataResult;
import com.aircas.ptr.foundry.ontology.OntologyClassGenerator;
import com.aircas.ptr.foundry.ontology.application.service.FunctionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;


@Api(tags = "function")
@RestController
@RequestMapping("/function")
public class FunctionHandler {

    @Resource
    FunctionService functionService;
    @Resource
    OntologyClassGenerator ontologyClassGenerator;

    @ApiOperation(value = "执行某个function")
    @PostMapping("/execute")
    public DataResult<Object> execute(@RequestBody HashMap map) {
        String functionName = (String) map.getOrDefault("functionName", null);
        Boolean isPreview = (Boolean) map.getOrDefault("isPreview", true);
        HashMap<String, Object> parameters= (HashMap<String, Object>) map.getOrDefault("parameters", null);
        return DataResult.ofData(functionService.handle(functionName, isPreview, parameters));
    }



    // 将函数
    @ApiOperation(value = "保存函数")
    @PostMapping("/saveFunction")
    public DataResult<Boolean> saveFunction(@RequestBody HashMap map) throws UnsupportedEncodingException {

    }

    //读取函数列表
    @ApiOperation(value = "读取函数列表")
    @PostMapping("/list")
    public DataResult<Boolean> functionList(@RequestBody HashMap map) throws UnsupportedEncodingException {

    }

    @ApiOperation(value = "保存代码")
    @PostMapping("/write")
    public DataResult<Boolean> saveCode(@RequestBody HashMap map) throws UnsupportedEncodingException {
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
}
