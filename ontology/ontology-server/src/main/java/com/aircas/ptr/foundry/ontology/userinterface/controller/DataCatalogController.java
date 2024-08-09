package com.aircas.ptr.foundry.ontology.userinterface.controller;

import com.aircas.ptr.foundry.common.base.DataResult;
import com.aircas.ptr.foundry.ontology.application.service.TableMetadataService;
import com.aircas.ptr.foundry.ontology.entity.vo.TableColumnDescVO;
import com.aircas.ptr.foundry.ontology.entity.vo.DatasourceTableVO;
import com.github.pagehelper.PageInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;


import javax.annotation.Resource;
import java.util.List;


@Api(tags = "数据源")
@RestController
@RequestMapping("/datasource")
public class DataCatalogController {

    @Resource
    private TableMetadataService tableMetadataService;

    @GetMapping("/column")
    @ApiOperation(value = "根据dataSourceId查询有哪些列")
    public DataResult<List<TableColumnDescVO>> query(@RequestParam @ApiParam(value = "dataSourceId", required = true) String dataSourceId) {
        return DataResult.ofData(tableMetadataService.getColumns(dataSourceId));
    }

    @GetMapping("/table/list")
    @ApiOperation(value = "查询数据源列表")
    public DataResult<List<DatasourceTableVO>> queryTables(){

        return DataResult.ofData(tableMetadataService.listTables());
    }

}