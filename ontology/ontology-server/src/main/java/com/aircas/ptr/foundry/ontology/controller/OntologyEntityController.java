package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import com.aircas.ptr.foundry.ontology.model.param.EntityActionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.EntityQueryParam;
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
    @ApiOperation(value = "根据主键查询实体所有属性详情")
    public RestResult<List<EntityPropertyDetailVO>> getEntityDetail(@RequestBody @Valid EntityQueryParam param) {
        return RestResult.ofData(entityService.getEntityDetail(param.getOntologyUniqueIdentifier(), param.getEntityPrimaryKey()));

    }

    @PostMapping("/link")
    @ApiOperation(value = "查询实体的关联关系")
    public RestResult<List<EntityLinkPropertyVO>> getEntityLinksByPrimaryKey(@RequestBody @Valid EntityQueryParam param) {
        return RestResult.ofData(entityService.getEntityLinksByPrimaryKey(param.getOntologyUniqueIdentifier(), param.getEntityPrimaryKey()));
    }

    @PostMapping("/action/execute")
    @ApiOperation(value = "执行实体的行为")
    public RestResult<String> executeAction(@RequestBody @Valid EntityActionExecuteParam param) throws Exception {
        return RestResult.ofData(entityService.executeAction(param));

    }



}
