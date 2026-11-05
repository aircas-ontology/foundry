package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.param.OntologyBuildFromDatasourceParam;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyBuildFromDatasourceResultVO;
import com.aircas.ptr.foundry.ontology.service.OntologyBuildFromDatasourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * 「数据源构建对象」向导的落库入口。前三个步骤由
 * {@link DatasourceConnectionController} 的 {@code /datasource/connection/list}、
 * {@code /datasource/connection/table/list}、{@code /datasource/connection/table/columns} 提供，
 * 这里只负责最后一步：把用户勾选的列一次性建成一个对象。
 * <p>
 * 一次调用只建一个对象；选了多张表即「一主多子」，主表属性归 {@code storageGroup=main}，
 * 子表属性用子表名作存储分组。
 * <p>
 * 注意本接口只建结构：数据湖里的实体表建出来是空的，外部库的实例数据不会自动搬进来；
 * 数据管道事件也不在这里发，需要前端照画布的做法再调 {@code POST /property/notify_build_pipeline}。
 */
@Tag(name = "数据源构建对象")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/datasource")
public class OntologyBuildFromDatasourceController {

    private final OntologyBuildFromDatasourceService ontologyBuildFromDatasourceService;

    @Operation(summary = "用外部数据源勾选的表与列创建一个对象")
    @PostMapping("/build_object")
    public RestResult<OntologyBuildFromDatasourceResultVO> buildObject(
            @RequestBody @Valid OntologyBuildFromDatasourceParam param) {
        return RestResult.ofData(ontologyBuildFromDatasourceService.buildObject(param));
    }
}
