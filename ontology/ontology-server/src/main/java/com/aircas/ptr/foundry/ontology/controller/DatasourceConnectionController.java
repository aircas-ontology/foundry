package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceConnectionVO;
import com.aircas.ptr.foundry.ontology.service.DatasourceConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据源连接配置管理
 * 对应 ontology.datasource_connection 表
 */
@Tag(name = "数据源连接配置")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/datasource-connection")
public class DatasourceConnectionController {

    private final DatasourceConnectionService datasourceConnectionService;

    @GetMapping("/list")
    @Operation(summary = "查询启用中的数据源连接列表")
    public RestResult<List<DatasourceConnectionVO>> listEnabled() {
        return RestResult.ofData(datasourceConnectionService.listEnabled());
    }
}
