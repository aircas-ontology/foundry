package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.DataResult;
import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.service.TableMetadataService;
import com.aircas.ptr.foundry.ontology.model.vo.TableColumnDescVO;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceTableVO;
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
    public RestResult<List<TableColumnDescVO>> query(@RequestParam @ApiParam(value = "dataSourceId", required = true) String dataSourceId) {
        //todo 增加 is primary key
        return RestResult.ofData(tableMetadataService.getColumns(dataSourceId));
    }

    @GetMapping("/table/list")
    @ApiOperation(value = "查询数据源列表")
    public RestResult<List<DatasourceTableVO>> queryTables(){

        return RestResult.ofData(tableMetadataService.listTables());
    }

}