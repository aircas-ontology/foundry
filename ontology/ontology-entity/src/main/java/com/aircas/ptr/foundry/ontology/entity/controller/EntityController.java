package com.aircas.ptr.foundry.ontology.entity.controller;


import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.entity.model.param.EntityCreateParam;
import com.aircas.ptr.foundry.ontology.entity.service.EntityService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;

@Api(tags = "实体管理接口")
@RestController
@RequestMapping("/entity")
public class EntityController {

    @Resource
    private EntityService entityService;

    @ApiOperation("创建实体：创建实体表、实体数据、实体节点")
    @ApiResponses({
            @ApiResponse(code = 200, message = "创建成功")
    })
    @PostMapping("/create")
    public RestResult createEntities(@RequestBody @Valid EntityCreateParam entityCreateParam) {
        entityService.createEntities(entityCreateParam);
        return RestResult.success();
    }

}
