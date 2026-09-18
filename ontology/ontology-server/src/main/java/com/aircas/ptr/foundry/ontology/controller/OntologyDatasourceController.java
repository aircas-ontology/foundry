package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceTableVO;
import com.aircas.ptr.foundry.ontology.model.vo.TableColumnDescVO;
import com.aircas.ptr.foundry.ontology.service.TableMetadataService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;


@Tag(name = "数据源")
@RestController
@RequestMapping("/datasource")
public class OntologyDatasourceController {

    @Resource
    private TableMetadataService tableMetadataService;

    @GetMapping("/column")
    @Operation(summary = "根据本体空间和表名查询字段信息")
    public RestResult<List<TableColumnDescVO>> getColumns(
            @RequestParam @Parameter(description = "spaceId") Integer spaceId,
            @RequestParam @Parameter(description = "dataSourceId") String dataSourceId) {
        return RestResult.ofData(tableMetadataService.getColumns(spaceId, dataSourceId));
    }

    @GetMapping("/table")
    @Operation(summary = "搜索查询本体空间下数据源列表")
    public RestResult<Page<DatasourceTableVO>> getTables(
            @RequestParam @Parameter(description = "spaceId") Integer spaceId,
            @RequestParam(required = false, defaultValue = "") @Parameter(description = "keyword") String keyword,
            @RequestParam(required = false, defaultValue = "1") @Parameter(description = "pageNum") Integer pageNum,
            @RequestParam(required = false, defaultValue = "1000") @Parameter(description = "pageSize") Integer pageSize) {
        return RestResult.ofData(tableMetadataService.getTables(spaceId, keyword, pageNum, pageSize));
    }


    @DeleteMapping("/drop_datasource/{schemaName}/{dataSourceId}")
    @Operation(summary = "删除表")
    public RestResult dropDataSource(@PathVariable("schemaName") String schemaName,
                                     @PathVariable("dataSourceId") String dataSourceId) {
        tableMetadataService.dropDataSource(schemaName, dataSourceId);
        return RestResult.success();
    }

    @DeleteMapping("/drop_columns/{schemaName}/{dataSourceId}")
    @Operation(summary = "删除列")
    public RestResult dropColumns(
            @PathVariable("schemaName") String schemaName,
            @PathVariable("dataSourceId") String dataSourceId,
            @RequestBody @Valid List<String> columns) {
        tableMetadataService.dropColumns(schemaName, dataSourceId, columns);
        return RestResult.success();
    }

}