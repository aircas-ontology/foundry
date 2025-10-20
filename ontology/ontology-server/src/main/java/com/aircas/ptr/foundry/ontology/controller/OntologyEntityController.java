package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import com.aircas.ptr.foundry.ontology.model.param.EntityQueryParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyEntityDeleteParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyEntityParam;
import com.aircas.ptr.foundry.ontology.model.vo.EntityActionVO;
import com.aircas.ptr.foundry.ontology.model.vo.EntityInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.EntityLinkPropertyVO;
import com.aircas.ptr.foundry.ontology.model.vo.EntityPropertyDetailVO;
import com.aircas.ptr.foundry.ontology.service.EntityService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

@Api(tags = "实体")
@RestController
@Validated
@RequestMapping("/entity")
public class OntologyEntityController {

    @Resource
    private EntityService entityService;


    @ApiOperation(value = "实体分页查询", notes = "分页查询本体下实体信息列表")
    @GetMapping("/list")
    public RestResult<Page<EntityInfoVO>> getEntities(
            @RequestParam(required = true, name = "ontologyUniqueIdentifier") @ApiParam(value = "本体id", required = true) @OntologyIdVerify String ontologyUniqueIdentifier,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return RestResult.ofData(entityService.getEntities(ontologyUniqueIdentifier, pageNum, pageSize));
    }


    @PostMapping("/detail")
    @ApiOperation(value = "根据实体主键查询某实体的所有属性详情")
    public RestResult<List<EntityPropertyDetailVO>> getEntityDetail(@RequestBody @Valid EntityQueryParam param) {
        return RestResult.ofData(entityService.getEntityDetail(param.getOntologyUniqueIdentifier(), param.getEntityPrimaryKey()));

    }

    @PostMapping("/link")
    @ApiOperation(value = "查询本体下实体的关联关系")
    public RestResult<List<EntityLinkPropertyVO>> getEntityLinksByPrimaryKey(@RequestBody @Valid EntityQueryParam param) {
        return RestResult.ofData(entityService.getEntityLinksByPrimaryKey(param.getOntologyUniqueIdentifier(), param.getEntityPrimaryKey()));
    }

    @PostMapping("/action")
    @ApiOperation(value = "查询本体下实体的行为")
    public RestResult<List<EntityActionVO>> getEntityActionsByPrimaryKey(@RequestBody @Valid EntityQueryParam param) {

        return RestResult.ofData(null);

    }


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
    public RestResult deleteOntologyEntity(@RequestBody @Valid OntologyEntityDeleteParam param) {
        return RestResult.success();
    }


    @GetMapping("/list/by_link")
    @ApiOperation(value = "依据本体关系id查询实体关系列表")
    public RestResult<List<EntityLinkPropertyVO>> getEntityLinksById(@RequestParam(required = true, name = "linkUniqueIdentifier") @ApiParam(name = "linkUniqueIdentifier", value = "关系id") String linkUniqueIdentifier) {
        return RestResult.success();
    }

}
