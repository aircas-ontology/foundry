package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import com.aircas.ptr.foundry.ontology.model.param.*;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.aircas.ptr.foundry.ontology.service.EntityService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.var;
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


    @ApiOperation(value = "根据多个本体下多个实体id查询实体主属性信息")
    @PostMapping("/query")
    public RestResult<List<EntityIdsQueryVO>> getByEntityIds(@RequestBody @Valid List<EntityIdsQueryParam> params) {
        return RestResult.ofData(entityService.getByEntityIds(params));
    }


    @ApiOperation(value = "实体分页查询", notes = "分页查询本体下实体主属性信息列表")
    @GetMapping("/list")
    public RestResult<Page<EntityInfoVO>> getEntities(
            @RequestParam(required = true, name = "ontologyUniqueIdentifier") @ApiParam(value = "本体id", required = true) @OntologyIdVerify String ontologyUniqueIdentifier,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return RestResult.ofData(entityService.getEntities(ontologyUniqueIdentifier, "", "", pageNum, pageSize, false));
    }


    @ApiOperation(value = "实体搜索", notes = "实体根据主属性值进行等值搜索")
    @PostMapping("/search")
    public RestResult<Page<EntityInfoVO>> searchEntities(@RequestBody @Valid EntitySearchParam param) {
        return RestResult.ofData(entityService.getEntities(
                param.getOntologyUniqueIdentifier(),
                param.getPropertyName(),
                param.getPropertyValue(),
                param.getPageNum(),
                param.getPageSize(),
                true));
    }


    @PostMapping("/detail")
    @ApiOperation(value = "根据实体id查询所有属性（按列返回，动态属性默认id倒序展示前10条）")
    public RestResult<List<EntityPropertyDetailVO>> getEntityDetail(@RequestBody @Valid EntityQueryParam param) {
        return RestResult.ofData(entityService.getEntityDetail(param.getOntologyUniqueIdentifier(), param.getEntityPrimaryKey()));
    }

    @PostMapping("/row_detail")
    @ApiOperation(value = "根据实体id查询所有属性（按行返回，每类动态属性默认展示前100条）")
    public RestResult<EntityPropertyRowDetailVO> getEntityPropertyRowDetail(@RequestBody @Valid EntityPropertyRowQueryParam param) {
        return RestResult.ofData(entityService.getEntityPropertyRowDetail(param));
    }

    @PostMapping("/link")
    @ApiOperation(value = "查询单本体单个实体所有关联关系")
    public RestResult<List<EntityLinkPropertyVO>> getEntityLinksByPrimaryKey(@RequestBody @Valid EntityQueryParam param) {
        return RestResult.ofData(entityService.getEntityLinksByPrimaryKey(param.getOntologyUniqueIdentifier(), param.getEntityPrimaryKey()));
    }


    @PostMapping("/all_link")
    @ApiOperation(value = "查询多本体下多个实体所有关联关系")
    public RestResult<List<EntityLinksVO>> getAllLinksByEntityIds(@RequestBody @Valid List<EntityIdsQueryParam> params) {
        return RestResult.ofData(entityService.getAllLinksByEntityIds(params));
    }


    @PostMapping("/action/execute")
    @ApiOperation(value = "执行实体的行为")
    public RestResult<String> executeAction(@RequestBody @Valid EntityActionExecuteParam param) throws Exception {
        return RestResult.ofData(entityService.executeAction(param));
    }


    @ApiOperation(value = "实体属性更新")
    @PutMapping("/update")
    public RestResult updateProperty(@RequestBody @Valid EntityUpdateParam param) {
        entityService.updateProperty(param);
        return RestResult.success();
    }

    @ApiOperation(value = "创建本体下所有实体节点")
    @PostMapping("/node")
    public RestResult createEntityNodes(@RequestBody @Valid OntologyIdentifierParam param) {
        entityService.createEntityNodes(param.getOntologyIdentifier());
        return RestResult.success();
    }

    @ApiOperation(value = "根据本体关系id创建所有实体节点关系")
    @PostMapping("/relation/{linkUniqIdentifier}")
    public RestResult createEntityRelations(@PathVariable String linkUniqIdentifier) {
        entityService.createEntityRelations(linkUniqIdentifier);
        return RestResult.success();
    }


    @ApiOperation(value = "补全单个实体节点和关系")
    @PostMapping("/completeNodeAndRelations")
    public RestResult completeEntityNodeAndRelations(@RequestBody @Valid EntityNodeAndRelationsCompleteParam param) {
        entityService.completeEntityNodeAndRelations(param.getOntologyUniqueIdentifier(), param.getEntityPropertyMap());
        return RestResult.success();
    }

    @ApiOperation(value = "删除单个实体节点和关系")
    @DeleteMapping("/deleteNodeAndRelations")
    public RestResult deleteEntityNodeAndRelations(@RequestBody @Valid EntityNodeAndRelationsDeleteParam param) {
        entityService.deleteEntityNodeAndRelations(param.getOntologyUniqueIdentifier(), param.getEntityPrimaryKey());
        return RestResult.success();
    }


    @ApiOperation(value = "初始化生成实体数据")
    @PostMapping("/generate")
    public RestResult generateEntities(@RequestBody @Valid EntityGenerateParam param) {
        entityService.generateEntities(param);
        return RestResult.success();
    }

    @ApiOperation(value = "实体属性通用查询")
    @PostMapping("/generic_query")
    public RestResult<Page<List<EntityPropertyGenericQueryVO>>> genericQuery(@RequestBody @Valid EntityPropertyGenericQueryParam param) {
        var res = entityService.genericQuery(param);
        return RestResult.ofData(res);
    }


}
