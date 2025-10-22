package com.aircas.ptr.foundry.ontology.entity.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.common.param.*;
import com.aircas.ptr.foundry.ontology.common.vo.EntityDetailVO;
import com.aircas.ptr.foundry.ontology.common.vo.EntityVO;
import com.aircas.ptr.foundry.ontology.entity.service.EntityTableService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

@Api(tags = "实体表管理接口")
@RestController
@RequestMapping("/table")
public class EntityTableController {

    @Resource
    private EntityTableService entityTableService;

    @ApiOperation("创建实体表、实体数据、实体节点和关系")
    @PostMapping("")
    public RestResult createEntities(@RequestBody @Valid EntityCreateParam entityCreateParam) {
        entityTableService.createEntities(entityCreateParam);
        return RestResult.success();
    }

    @ApiOperation("复制实体表、实体数据、实体节点、关系")
    @PostMapping("/copy")
    public RestResult copyEntities(@RequestBody @Valid EntityCopyParam entityCopyParam) {
        entityTableService.copyEntities(entityCopyParam);
        return RestResult.success();
    }

    @ApiOperation("删除实体表、实体数据、实体节点和关系")
    @DeleteMapping("/{tableName}")
    public RestResult deleteEntitiesByTableName(@PathVariable(required = true, name = "tableName") String tableName) {
        entityTableService.deleteEntitiesByTableName(tableName);
        return RestResult.success();
    }

    @ApiOperation("删除实体节点和关系")
    @DeleteMapping("/node/{tableName}")
    public RestResult deleteEntityNodeByTableName(@PathVariable(required = true, name = "tableName") String tableName) {
        entityTableService.deleteNodesByTableName(tableName);
        return RestResult.success();
    }


    @ApiOperation("增加列")
    @PostMapping("/column")
    public RestResult createColumns(@RequestBody @Valid EntityColumnCreateParam param) {
        entityTableService.createColumns(param);
        return RestResult.success();
    }

    @ApiOperation("删除列")
    @DeleteMapping("/column")
    public RestResult deleteColumn(@RequestBody @Valid EntityDataSourceParam param) {
        return RestResult.success();
    }


    @ApiOperation("修改table关联健")
    @PutMapping("/column_relation")
    public RestResult modifyColumnRelation(@RequestBody @Valid TableColumnRelationUpdateParam param) {
        return RestResult.success();
    }


    @ApiOperation("分页查询表数据")
    @GetMapping("/record")
    public RestResult<Page<EntityVO>> queryEntitiesByTableName(@RequestParam(required = true, name = "tableName") String tableName,
                                                               @RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                                               @RequestParam(required = false, defaultValue = "10") Integer pageSize) {

        return RestResult.ofData(entityTableService.queryEntitiesByTableName(tableName, pageNum, pageSize));
    }


    @ApiOperation("查询实体详细数据")
    @PostMapping("/record_detail")
    public RestResult<List<EntityDetailVO>> queryEntityDetail(@RequestBody @Valid EntityDetailQueryParam param) {
        return RestResult.ofData(entityTableService.queryEntityDetail(param));
    }



    @ApiOperation("新增实体记录")
    @PostMapping("/record")
    public RestResult createEntity(@RequestBody @Valid EntityRecordParam param) {
        return RestResult.success();
    }

    @ApiOperation("修改实体记录")
    @PutMapping("/record")
    public RestResult updateEntity(@RequestBody @Valid EntityRecordParam param) {
        return RestResult.success();
    }

    @ApiOperation("删除实体记录")
    @DeleteMapping("/record/{tableName}/{primaryValue}")
    public RestResult deleteEntity(@PathVariable(name = "tableName", required = true) String tableName,
                                   @PathVariable(name = "primaryValue", required = true) String primaryValue) {
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