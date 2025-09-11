package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.DataResult;
import com.aircas.ptr.foundry.ontology.service.ObjectService;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.aircas.ptr.foundry.ontology.model.param.*;
import com.github.pagehelper.PageInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.apache.commons.httpclient.HttpException;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;

@Api(tags = "实体")
@RestController
@RequestMapping("/object")
public class ObjectController {

    @Resource
    ObjectService objectService;

    @ApiOperation(value = "实体分页查询（简要信息）", notes = "查询某个本体下面所有的实体列表，只显示主键和名称")
    @GetMapping("/list/simple")
    public DataResult<PageInfo<DirectoryItemVO>> queryDirectory(
            @RequestParam @ApiParam(value = "本体id") String uniqueIdentifier,
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

    @GetMapping("/link_object")
    @ApiOperation(value = "查询本体的链接的所有动态数据")
    public DataResult<ObjectWithLinkedInfoVO> queryObjectWithLinkedInfoByPrimaryKey(@RequestParam String uniqueIdentifier, @RequestParam String key) {
        ObjectWithLinkedInfoVO objectWithLinkedInfoVO = objectService.queryObjectWithLinkedInfoByPrimaryKey(uniqueIdentifier, key);
        return DataResult.ofData(objectWithLinkedInfoVO);
    }

    @ApiOperation(value = "实体分页查询", notes = "查询该本体下所有实体详情列表")
    @GetMapping("/list")
    public DataResult<PageInfo<Map<String, Object>>> queryObject(
            @RequestParam @ApiParam(value = "本体id") String ontologyUniqueIdentifier,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size) {

        return DataResult.ofData(objectService.queryObjectList(ontologyUniqueIdentifier, page, size));
    }


    @ApiOperation(value = "更新实体属性", notes = "更新本体下指定实体的属性信息")
    @PostMapping("/updateOntologyEntity")
    public DataResult updateOntologyEntity(@RequestBody UpdateOntologyEntityParam param) {
        return DataResult.ofData(objectService.updateObjectData(param.getOntologyUniqueIdentifier(), param.getUpdateData(), param.getUpdateWhere()));
    }

    @PostMapping("/list/by_filter")
    @ApiOperation(value = "实体分页条件查询", notes = "根据条件查询本体下实体详情列表")
    public DataResult<PageInfo<Map<String, Object>>> queryObjectByFilter(@RequestBody OntologyObjectQueryParam param) {


        return DataResult.ofData(objectService.queryObjectByFilter(param.getOntologyApi(), param.getFilter(), param.getPage(), param.getSize(), param.getSorts()));
    }

    @PostMapping("/list/by_link")
    @ApiOperation(value = "实体查询，依据关系id", notes = "根据关系id查询本体下实体详情列表")
    public DataResult<PageInfo<Map<String, Object>>> queryObjectByFilter(@RequestBody OntologyObjectQueryByLinkParam param) {

        return DataResult.ofData(objectService.queryObjectByLink(param.getLinkId(), param.getOntologyId(), param.getObj(), param.getPage(), param.getSize()));
    }

    public void test() throws HttpException {

    }
}
