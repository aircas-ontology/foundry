package com.aircas.ptr.foundry.ontology.userinterface.controller;

import com.aircas.ptr.foundry.common.base.DataResult;
import com.aircas.ptr.foundry.ontology.application.service.TableMetadataService;
import com.aircas.ptr.foundry.ontology.entity.vo.TableColumnDescVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.*;


import javax.annotation.Resource;
import java.util.List;


@Api(tags = "数据源")
@RestController
@RequestMapping("/dataCatalog")
public class DataCatalogController {

    @Resource
    private TableMetadataService tableMetadataService;

    @GetMapping("/queryColumns")
    @ApiOperation(value = "根据dataSourceId查询有哪些列")
    public DataResult<List<TableColumnDescVO>> query(@RequestParam @ApiParam(value = "dataSourceId", required = true) String dataSourceId) {
        return DataResult.ofData(tableMetadataService.getColumns(dataSourceId));
    }

}