package com.aircas.ptr.foundry.ontology.entity.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.common.param.EntityRelationCreateParam;
import com.aircas.ptr.foundry.ontology.common.vo.EntityRelationVO;
import com.aircas.ptr.foundry.ontology.entity.service.EntityNodeService;
import com.aircas.ptr.foundry.ontology.entity.service.EntityTableService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

@Api(tags = "实体节点管理接口")
@RestController
@RequestMapping("/node")
public class EntityNodeController {

    @Resource
    private EntityNodeService entityNodeService;



    @ApiOperation("创建实体关系")
    @PostMapping("/relation")
    public RestResult createRelations(@RequestBody @Valid EntityRelationCreateParam entityCreateParam) {
        entityNodeService.createEntityRelation(entityCreateParam.getEntityTableFrom(), entityCreateParam.getEntityTableTo(), entityCreateParam.getRelationType());
        return RestResult.success();
    }

    @ApiOperation("查询实体关系")
    @GetMapping("/relation")
    public RestResult<List<EntityRelationVO>> queryRelations(@RequestParam(name = "tableName", required = true) String tableName,
                                                             @RequestParam(name = "primaryKeyValue", required = true) Object primaryKeyValue) {
        return RestResult.ofData(entityNodeService.queryRelations(tableName, primaryKeyValue));
    }

}
