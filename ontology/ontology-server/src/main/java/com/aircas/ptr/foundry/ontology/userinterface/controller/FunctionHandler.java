package com.aircas.ptr.foundry.ontology.userinterface.controller;

import com.aircas.ptr.foundry.common.base.DataResult;
import com.aircas.ptr.foundry.ontology.OntologyClassGenerator;
import com.aircas.ptr.foundry.ontology.application.service.FunctionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;


@Api(tags = "function")
@RestController
@RequestMapping("/function")
public class FunctionHandler {

    @Resource
    FunctionService functionService;
    @Resource
    OntologyClassGenerator ontologyClassGenerator;

    @ApiOperation(value = "执行一段script")
    @PostMapping("/execution")
    public DataResult<Object> run(@RequestBody HashMap map) {
        String filePath = (String) map.getOrDefault("classFilePath", null);
        HashMap<String, Object> parameters= (HashMap<String, Object>) map.getOrDefault("parameters", null);
        return DataResult.ofData(functionService.handle(filePath, parameters));
    }
}
