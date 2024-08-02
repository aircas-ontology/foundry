package com.aircas.ptr.foundry.ontology.userinterface.controller;

import com.aircas.ptr.foundry.common.base.DataResult;
import com.aircas.ptr.foundry.ontology.application.service.ObjectService;
import com.aircas.ptr.foundry.ontology.entity.vo.*;
import com.github.pagehelper.PageInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@Api(tags = "实体")
@RestController
@RequestMapping("/object")
public class ObjectController {

    @Resource
    ObjectService objectService;

    @ApiOperation(value = "实体分页查询（简要信息）", notes = "查询某个本体下面所有的实体列表，只显示主键和名称")
    @GetMapping("/list/simple")
    public DataResult<PageInfo<DirectoryItemVO>> queryDirectory(
            @RequestParam(value = "本体id", required = true) String uniqueIdentifier,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        PageInfo<DirectoryItemVO> OntologyInfoList = objectService.queryDirectories(uniqueIdentifier, page, size);
        return DataResult.ofData(OntologyInfoList);
    }


    @GetMapping("/info")
    @ApiOperation(value = "根据主键查询某实体的所有属性")
    public DataResult<ObjectOneInfoVO> queryObjectByPrimaryKey(@RequestParam String uniqueIdentifier, @RequestParam String key) {
        ObjectOneInfoVO objectOneInfoVO = objectService.queryObjectByPrimaryKey(uniqueIdentifier, key);
        return DataResult.ofData(objectOneInfoVO);
    }

    @GetMapping("/queryObjectWithLinkedInfoByPrimaryKey")
    public DataResult<ObjectWithLinkedInfoVO> queryObjectWithLinkedInfoByPrimaryKey(@RequestParam String uniqueIdentifier, @RequestParam String key) {
        ObjectWithLinkedInfoVO objectWithLinkedInfoVO = objectService.queryObjectWithLinkedInfoByPrimaryKey(uniqueIdentifier, key);
        return DataResult.ofData(objectWithLinkedInfoVO);
    }

    @ApiOperation(value = "实体分页查询", notes = "查询该本体下所有实体详情列表")
    @GetMapping("/list")
    public DataResult<PageInfo<Map<String, Object>>> queryObjectByPage(
            @RequestParam(value = "本体id", required = true) String ontologyUniqueIdentifier,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size) {

        return DataResult.ofData(objectService.queryObjectList(ontologyUniqueIdentifier, page, size));
    }
}
