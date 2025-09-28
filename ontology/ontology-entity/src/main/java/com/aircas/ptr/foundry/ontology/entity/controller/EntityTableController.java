package com.aircas.ptr.foundry.ontology.entity.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.common.param.DataSourceParam;
import com.aircas.ptr.foundry.ontology.common.param.EntityCreateParam;
import com.aircas.ptr.foundry.ontology.common.param.TableColumnRelationUpdateParam;
import com.aircas.ptr.foundry.ontology.entity.service.EntityTableService;
import com.aircas.ptr.foundry.ontology.entity.service.PostgresService;
import io.swagger.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

@Api(tags = "实体表管理接口")
@RestController
@RequestMapping("/table")
public class EntityTableController {
    
    @Autowired
    private PostgresService postgresService;

    @Resource
    private EntityTableService entityTableService;

    @ApiOperation("创建实体表、实体数据、实体节点和关系")
    @ApiResponses({
            @ApiResponse(code = 200, message = "创建成功")
    })
    @PostMapping("")
    public RestResult createEntities(@RequestBody @Valid EntityCreateParam entityCreateParam) {
        entityTableService.createEntities(entityCreateParam);
        return RestResult.success();
    }

    @ApiOperation("删除实体表、实体数据、实体节点和关系")
    @DeleteMapping("/{tableName}")
    public RestResult deleteEntitiesByTableName(@PathVariable(required = true,name = "tableName") String tableName) {
        return RestResult.success();
    }


    @ApiOperation("增加列")
    @PostMapping("/column")
    public RestResult createColumn(@RequestBody @Valid DataSourceParam param) {
        return RestResult.success();
    }

    @ApiOperation("删除列")
    @DeleteMapping("/column")
    public RestResult deleteColumn(@RequestBody @Valid DataSourceParam param) {
        return RestResult.success();
    }


    @ApiOperation("修改table关联健")
    @PutMapping("/column_relation")
    public RestResult modifyColumnRelation(@RequestBody @Valid TableColumnRelationUpdateParam param) {
        return RestResult.success();
    }





//    @ApiOperation("创建数据表")
//    @ApiResponses({
//        @ApiResponse(code = 200, message = "创建成功"),
//        @ApiResponse(code = 400, message = "参数错误"),
//        @ApiResponse(code = 409, message = "表已存在")
//    })
//    @PostMapping("/tables")
//    public void createTable(@ApiParam("表结构信息") @RequestBody TableCreateDTO tableInfo) {
//        postgresService.createTable(tableInfo);
//    }
//
//    @ApiOperation("获取表结构信息")
//    @GetMapping("/tables/{tableName}")
//    public Map<String, Object> getTableInfo(@ApiParam("表名") @PathVariable String tableName) {
//        return postgresService.getTableInfo(tableName);
//    }
//
//    @ApiOperation("检查表是否存在")
//    @GetMapping("/tables/{tableName}/exists")
//    public boolean checkTableExists(@ApiParam("表名") @PathVariable String tableName) {
//        return postgresService.isTableExists(tableName);
//    }
//
//    @ApiOperation("修改表结构")
//    @PutMapping("/tables/{tableName}")
//    public void alterTable(
//            @ApiParam("表名") @PathVariable String tableName,
//            @ApiParam("新增列") @RequestParam(required = false) List<TableCreateDTO.FieldDTO> addColumns,
//            @ApiParam("删除列") @RequestParam(required = false) List<String> dropColumns,
//            @ApiParam("修改列") @RequestParam(required = false) List<TableCreateDTO.FieldDTO> modifyColumns) {
//        postgresService.alterTable(tableName, addColumns, dropColumns, modifyColumns);
//    }
//
//    @ApiOperation("删除表")
//    @DeleteMapping("/tables/{tableName}")
//    public void dropTable(@ApiParam("表名") @PathVariable String tableName) {
//        postgresService.dropTable(tableName);
//    }
//
//    @ApiOperation("灵活查询表数据")
//    @ApiResponses({
//        @ApiResponse(code = 200, message = "查询成功"),
//        @ApiResponse(code = 400, message = "参数错误"),
//        @ApiResponse(code = 404, message = "表不存在")
//    })
//    @PostMapping("/tables/{tableName}/query")
//    public List<Map<String, Object>> queryTable(
//            @ApiParam("表名") @PathVariable String tableName,
//            @ApiParam("查询条件") @RequestBody TableQueryDTO queryDTO) {
//        return postgresService.queryTable(tableName, queryDTO);
//    }
//
//    @ApiOperation("获取表数据行数")
//    @ApiResponses({
//        @ApiResponse(code = 200, message = "查询成功"),
//        @ApiResponse(code = 404, message = "表不存在")
//    })
//    @GetMapping("/tables/{tableName}/count")
//    public long getTableRowCount(@ApiParam("表名") @PathVariable String tableName) {
//        return postgresService.getTableRowCount(tableName);
//    }
//
//    @ApiOperation("批量插入数据到表")
//    @ApiResponses({
//        @ApiResponse(code = 200, message = "插入成功"),
//        @ApiResponse(code = 404, message = "表不存在"),
//        @ApiResponse(code = 400, message = "参数错误")
//    })
//    @PostMapping("/tables/{tableName}/batch")
//    public int batchInsertData(
//            @ApiParam("表名") @PathVariable String tableName,
//            @ApiParam("数据对象列表") @RequestBody List<Map<String, Object>> dataList) {
//        return postgresService.batchInsertData(tableName, dataList);
//    }
} 