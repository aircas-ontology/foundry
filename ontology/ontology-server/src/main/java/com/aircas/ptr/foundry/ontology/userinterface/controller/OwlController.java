package com.aircas.ptr.foundry.ontology.userinterface.controller;

import com.aircas.ptr.foundry.common.base.DataResult;
import com.aircas.ptr.foundry.ontology.application.service.ObjectService;
import com.aircas.ptr.foundry.ontology.entity.vo.*;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@Api(tags = "以OWL格式返回本体")
@RestController
@RequestMapping("/owl")
public class OwlController {

    @Resource
    ObjectService objectService;

    @ApiOperation("根据resource url 返回其owl")
    @GetMapping("/getResource")
    public ResponseEntity<String> queryObjectByPrimaryKey(@RequestParam String uri) {
        return null;
    }
}
