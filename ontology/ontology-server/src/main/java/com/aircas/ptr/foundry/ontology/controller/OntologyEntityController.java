package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.param.OntologyEntityDeleteParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyEntityParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyObjectQueryByLinkParam;
import com.aircas.ptr.foundry.ontology.model.vo.EntityInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.EntityLinkVO;
import com.aircas.ptr.foundry.ontology.service.ObjectService;
import com.github.pagehelper.PageInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import jdk.nashorn.internal.objects.annotations.Getter;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@Api(tags = "实体")
@RestController
@RequestMapping("/entity")
public class OntologyEntityController {

    @Resource
    ObjectService objectService;


    @ApiOperation(value = "实体分页查询", notes = "查询该本体下所有实体详情列表")
    @GetMapping("/list")
    public RestResult<PageInfo<EntityInfoVO>> queryEntity(
            @RequestParam(required = true, name = "ontologyUniqueIdentifier") @ApiParam(value = "本体id", required = true) String ontologyUniqueIdentifier,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size) {

        return RestResult.success();
    }

//    @ApiOperation(value = "实体分页查询（简要信息）", notes = "查询某个本体下面所有的实体列表，只显示主键和名称")
//    @GetMapping("/list/simple")
//    public DataResult<PageInfo<DirectoryItemVO>> queryDirectory(
//            @RequestParam @ApiParam(value = "本体id") String uniqueIdentifier,
//            @RequestParam(required = false) Integer page,
//            @RequestParam(required = false) Integer size) {
//        PageInfo<DirectoryItemVO> OntologyInfoList = objectService.queryDirectories(uniqueIdentifier, page, size);
//        return DataResult.ofData(OntologyInfoList);
//    }


    @GetMapping
    @ApiOperation(value = "根据实体主键查询某实体的所有属性")
    public RestResult<EntityInfoVO> queryEntityByPrimaryKey(@RequestParam(required = true, name = "ontologyUniqueIdentifier") @ApiParam(name = "ontologyUniqueIdentifier", value = "本体id") String ontologyUniqueIdentifier,
                                                            @RequestParam(required = true, name = "entityPrimaryKey") @ApiParam(name = "entityPrimaryKey", value = "实体primary key") String entityPrimaryKey) {
        return RestResult.success();

    }

    @GetMapping("/link")
    @ApiOperation(value = "查询本体下实体的关联关系")
    public RestResult<List<EntityLinkVO>> queryEntityLinkByPrimaryKey(@RequestParam(required = true, name = "ontologyUniqueIdentifier") @ApiParam(name = "ontologyUniqueIdentifier", value = "本体id") String ontologyUniqueIdentifier,
                                                                      @RequestParam(required = false, name = "entityPrimaryKey") @ApiParam(name = "entityPrimaryKey", value = "实体primary key") String entityPrimaryKey) {
        return RestResult.success();

    }

//    @ApiOperation(value = "实体分页查询", notes = "查询该本体下所有实体详情列表")
//    @GetMapping("/list")
//    public DataResult<PageInfo<Map<String, Object>>> queryObject(
//            @RequestParam @ApiParam(value = "本体id") String ontologyUniqueIdentifier,
//            @RequestParam(required = false, defaultValue = "1") Integer page,
//            @RequestParam(required = false, defaultValue = "10") Integer size) {
//
//        return DataResult.ofData(objectService.queryObjectList(ontologyUniqueIdentifier, page, size));
//    }

    @ApiOperation(value = "新增实体", notes = "更新本体下指定实体的属性信息")
    @PostMapping("")
    public RestResult createOntologyEntity(@RequestBody @Valid OntologyEntityParam param) {
        return RestResult.success();
    }

    @ApiOperation(value = "更新实体属性值", notes = "更新本体下指定实体的属性信息")
    @PutMapping("")
    public RestResult updateOntologyEntity(@RequestBody @Valid OntologyEntityParam param) {
        return RestResult.success();
    }

    @ApiOperation(value = "删除实体属性", notes = "更新本体下指定实体的属性信息")
    @DeleteMapping("")
    public RestResult OntologyEntity(@RequestBody @Valid OntologyEntityDeleteParam param) {
        return RestResult.success();
    }

//    @PostMapping("/list/by_filter")
//    @ApiOperation(value = "实体分页条件查询", notes = "根据条件查询本体下实体详情列表")
//    public DataResult<PageInfo<Map<String, Object>>> queryObjectByFilter(@RequestBody OntologyObjectQueryParam param) {
//        return DataResult.ofData(objectService.queryObjectByFilter(param.getOntologyApi(), param.getFilter(), param.getPage(), param.getSize(), param.getSorts()));
//    }

    @GetMapping("/list/by_link")
    @ApiOperation(value = "依据本体关系id查询实体关系列表")
    public RestResult<List<EntityLinkVO>> queryEntityLinkById(@RequestParam(required = true, name = "linkUniqueIdentifier") @ApiParam(name = "linkUniqueIdentifier", value = "关系id") String linkUniqueIdentifier) {
        return RestResult.success();
    }

}
