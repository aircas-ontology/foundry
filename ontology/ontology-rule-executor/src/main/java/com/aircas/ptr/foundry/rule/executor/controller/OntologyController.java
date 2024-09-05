package com.aircas.ptr.foundry.rule.executor.controller;

import com.aircas.ptr.foundry.common.base.ApiResult;
import com.aircas.ptr.foundry.rule.executor.entity.OntologyMeta;
import com.aircas.ptr.foundry.rule.executor.entity.OntologyProperty;
import com.aircas.ptr.foundry.rule.executor.service.IOntologyServer;
import com.aircas.ptr.foundry.rule.executor.utils.LocalCacheUtils;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/ontology")
public class OntologyController {

    @Autowired
    private IOntologyServer ontologyServer;

    @ApiOperation(value = "刷新实体属性信息（主键）", notes = "")
    @GetMapping("/refresh/propery")
    public ApiResult loadOntologyProperty(){
        List<OntologyProperty> propertyList = ontologyServer.getAllOntologProprety();
        Map<String,List<OntologyProperty>> map = propertyList.stream().collect(Collectors.groupingBy(OntologyProperty::getOntologyUniqueIdentifier));
        LocalCacheUtils.ontologyPropertyMap = map;
        return ApiResult.success();
    }


    @ApiOperation(value = "刷新实体meta信息", notes = "")
    @GetMapping("/refresh/meta")
    public ApiResult loadOntologyMeta(){
        List<OntologyMeta> metaList =  ontologyServer.getAllOntologies();
//        Map<String,OntologyMeta> datamap = metaList.stream().collect(Collectors.toMap(OntologyMeta::getBackingDatasourceId, Function.identity()));
        Map<String,List<OntologyMeta>> datamap = metaList.stream().collect(Collectors.groupingBy(OntologyMeta::getBackingDatasourceId));
        LocalCacheUtils.ontologyMetaMap = datamap;
        return ApiResult.success();
    }
}
