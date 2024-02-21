package com.aircas.ptr.foundry.ontology.userinterface.controller;

import com.aircas.ptr.foundry.common.base.DataResult;
import com.aircas.ptr.foundry.common.constant.Status;
import com.aircas.ptr.foundry.ontology.application.service.ObjectService;
import com.aircas.ptr.foundry.ontology.application.service.OntologyService;
import com.aircas.ptr.foundry.ontology.entity.vo.*;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@Api(tags = "Objects")
@RestController
@RequestMapping("/Objects")
public class ObjectController {

    @Resource
    ObjectService objectService;

    @ApiOperation("查询某个本体下面所有的object instance")
    @GetMapping("/queryDirectory")
    public DataResult<List<DirectoryItemVO>> queryDirectory(@RequestParam String uniqueIdentifier) {
        List<DirectoryItemVO> OntologyInfoList = objectService.queryDirectories(uniqueIdentifier);
        return DataResult.ofData(OntologyInfoList);
    }


    @GetMapping("/queryObjectByPrimaryKey")
    public DataResult<ObjectValueVo> queryObjectByPrimaryKey(@RequestParam String uniqueIdentifier, @RequestParam String key) {
        ObjectValueVo objectValueVo = objectService.queryObjectByPrimaryKey(uniqueIdentifier, key);
        return DataResult.ofData(objectValueVo);
    }

    @GetMapping("/queryObjectWithLinkedInfoByPrimaryKey")
    public DataResult<ObjectWithLinkedInfoVO> queryObjectWithLinkedInfoByPrimaryKey(@RequestParam String uniqueIdentifier, @RequestParam String key) {
        ObjectWithLinkedInfoVO objectWithLinkedInfoVO = objectService.queryObjectWithLinkedInfoByPrimaryKey(uniqueIdentifier, key);
        return DataResult.ofData(objectWithLinkedInfoVO);
    }

}
