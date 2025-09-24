package com.aircas.ptr.foundry.ontology.entity.controller;


import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.entity.model.param.EntityCreateParam;
import com.aircas.ptr.foundry.ontology.entity.model.param.EntityRecordParam;
import com.aircas.ptr.foundry.ontology.entity.model.vo.EntityVO;
import com.aircas.ptr.foundry.ontology.entity.service.EntityService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

@Api(tags = "实体管理接口")
@RestController
@RequestMapping("/record")
public class EntityRecordController {


    @ApiOperation("查询表数据（实体、实体属性历史数据）")
    @GetMapping("")
    public RestResult<List<EntityVO>> queryEntitiesByTableName(@RequestParam(required = true,name = "tableName") String tableName) {
        return RestResult.success();
    }


    @ApiOperation("新增实体记录")
    @PostMapping("")
    public RestResult createEntity(@RequestBody @Valid EntityRecordParam param) {
        return RestResult.success();
    }

    @ApiOperation("修改实体记录")
    @PutMapping("")
    public RestResult updateEntity(@RequestBody @Valid EntityRecordParam param) {
        return RestResult.success();
    }

    @ApiOperation("删除实体记录")
    @DeleteMapping("/{tableName}/{primaryValue}")
    public RestResult deleteEntity(@PathVariable(name = "tableName",required = true) String tableName,
                                   @PathVariable(name = "primaryValue",required = true) String primaryValue) {
        return RestResult.success();
    }




}
