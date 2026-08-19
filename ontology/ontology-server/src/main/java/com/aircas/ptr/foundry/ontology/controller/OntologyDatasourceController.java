package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceTableVO;
import com.aircas.ptr.foundry.ontology.model.vo.TableColumnDescVO;
import com.aircas.ptr.foundry.ontology.service.TableMetadataService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;


@Api(tags = "数据源")
@RestController
@RequestMapping("/datasource")
public class OntologyDatasourceController {

    @Resource
    private TableMetadataService tableMetadataService;

    @GetMapping("/column")
    @ApiOperation(value = "根据本体空间和表名查询字段信息")
    public RestResult<List<TableColumnDescVO>> getColumns(
            @RequestParam @ApiParam(value = "spaceId", required = true) Integer spaceId,
            @RequestParam @ApiParam(value = "dataSourceId", required = true) String dataSourceId) {
        return RestResult.ofData(tableMetadataService.getColumns(spaceId, dataSourceId));
    }

    @GetMapping("/table")
    @ApiOperation(value = "搜索查询本体空间下数据源列表")
    public RestResult<Page<DatasourceTableVO>> getTables(
            @RequestParam @ApiParam(value = "spaceId", required = true) Integer spaceId,
            @RequestParam(required = false, defaultValue = "") @ApiParam(value = "keyword", required = false) String keyword,
            @RequestParam(required = false, defaultValue = "1") @ApiParam(value = "pageNum", required = false) Integer pageNum,
            @RequestParam(required = false, defaultValue = "1000") @ApiParam(value = "pageSize", required = false) Integer pageSize) {
        return RestResult.ofData(tableMetadataService.getTables(spaceId, keyword, pageNum, pageSize));
    }


    @DeleteMapping("/drop_datasource/{schemaName}/{dataSourceId}")
    @ApiOperation(value = "删除表")
    public RestResult dropDataSource(@PathVariable("schemaName") String schemaName,
                                     @PathVariable("dataSourceId") String dataSourceId) {
        tableMetadataService.dropDataSource(schemaName, dataSourceId);
        return RestResult.success();
    }

    @DeleteMapping("/drop_columns/{schemaName}/{dataSourceId}")
    @ApiOperation(value = "删除列")
    public RestResult dropColumns(
            @PathVariable("schemaName") String schemaName,
            @PathVariable("dataSourceId") String dataSourceId,
            @RequestBody @Valid List<String> columns) {
        tableMetadataService.dropColumns(schemaName, dataSourceId, columns);
        return RestResult.success();
    }

}