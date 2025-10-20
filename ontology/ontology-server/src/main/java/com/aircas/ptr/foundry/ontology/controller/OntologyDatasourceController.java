package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceTableVO;
import com.aircas.ptr.foundry.ontology.model.vo.TableColumnDescVO;
import com.aircas.ptr.foundry.ontology.service.TableMetadataService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
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

}