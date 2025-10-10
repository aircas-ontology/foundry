package com.aircas.ptr.foundry.ontology.entity.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.common.param.EntityRelationCreateParam;
import com.aircas.ptr.foundry.ontology.entity.service.EntityNodeService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;

@Api(tags = "本体管理接口")
@RestController
@RequestMapping("/node")
public class EntityNodeController {

    @Resource
    private EntityNodeService entityNodeService;

    @ApiOperation("创建实体关系")
    @PostMapping("/relation")
    public RestResult createEntityRelations(@RequestBody @Valid EntityRelationCreateParam entityCreateParam) {
        entityNodeService.createEntityRelation(entityCreateParam.getEntityTableFrom(), entityCreateParam.getEntityTableTo(), entityCreateParam.getRelationType());
        return RestResult.success();
    }

}
