package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceTableVO;
import com.aircas.ptr.foundry.ontology.model.vo.TableColumnDescVO;
import com.aircas.ptr.foundry.ontology.service.TableMetadataService;
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
    @ApiOperation(value = "根据dataSourceId查询有哪些列")
    public RestResult<List<TableColumnDescVO>> getColumns(@RequestParam @ApiParam(value = "dataSourceId", required = true) String dataSourceId) {
        return RestResult.ofData(tableMetadataService.getColumns(dataSourceId));
    }

    @GetMapping("/table")
    @ApiOperation(value = "查询数据源列表")
    public RestResult<List<DatasourceTableVO>> getTables() {
        return RestResult.ofData(tableMetadataService.listTables());
    }


    @DeleteMapping("/drop_datasource/{dataSourceId}")
    @ApiOperation(value = "删除表")
    public RestResult dropDataSource(@PathVariable("dataSourceId") String dataSourceId) {
        tableMetadataService.dropDataSource(dataSourceId);
        return RestResult.success();
    }

    @DeleteMapping("/drop_columns/{dataSourceId}")
    @ApiOperation(value = "删除列")
    public RestResult dropColumns(@PathVariable("dataSourceId") String dataSourceId, @RequestBody @Valid List<String> columns) {
        tableMetadataService.dropColumns(dataSourceId, columns);
        return RestResult.success();
    }

}