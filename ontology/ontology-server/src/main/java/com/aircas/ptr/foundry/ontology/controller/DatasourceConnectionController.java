package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.param.DatasourceConnectionParam;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceConnectionItemVO;
import com.aircas.ptr.foundry.ontology.service.DatasourceConnectionService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * 数据源连接配置管理接口：外部数据库连接的增删改查与连通性测试。
 * <p>
 * 表已从数据湖库迁到主库 {@code ontology.datasource_connection}；这些配置是平台级元数据，
 * 与数据湖里按空间动态生成的实体业务表无关。接口形态对齐
 * {@link FunctionOperatorTemplateController}：id 与时间字段由后台生成，更新走 query 传 id。
 * <p>
 * 密码只接受写入、任何查询接口都不返回；本路径不在 {@code WebMvcConfig} 的鉴权白名单内，默认要求 JWT。
 */
@Tag(name = "数据源连接配置")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/datasource/connection")
public class DatasourceConnectionController {

    private final DatasourceConnectionService datasourceConnectionService;

    @Operation(summary = "分页查询数据源列表（keyword 按名称/数据库类型模糊匹配；status 不传查全部，1 启用 0 禁用；不返回密码）")
    @GetMapping("/list")
    public RestResult<IPage<DatasourceConnectionItemVO>> list(
            @RequestParam(required = false, defaultValue = "") @Parameter(description = "关键词：名称或数据库类型") String keyword,
            @RequestParam(required = false) @Parameter(description = "状态过滤：1 启用，0 禁用，不传查全部") Integer status,
            @RequestParam(required = false, defaultValue = "1") @Parameter(description = "页码，从 1 开始") Integer pageNum,
            @RequestParam(required = false, defaultValue = "20") @Parameter(description = "每页条数") Integer pageSize) {
        return RestResult.ofData(datasourceConnectionService.pageDatasources(keyword, status, pageNum, pageSize));
    }

    @Operation(summary = "查询单个数据源（编辑表单回显用；不返回密码）")
    @GetMapping("/detail")
    public RestResult<DatasourceConnectionItemVO> detail(
            @RequestParam @Parameter(description = "数据源 id") Integer id) {
        return RestResult.ofData(datasourceConnectionService.getDatasource(id));
    }

    @Operation(summary = "新增数据源（id、创建/更新时间由后台生成，请求体不接收；password 必填；status 不传按启用落库）")
    @PostMapping
    public RestResult<DatasourceConnectionItemVO> create(@RequestBody @Valid DatasourceConnectionParam param) {
        return RestResult.ofData(datasourceConnectionService.createDatasource(param));
    }

    @Operation(summary = "修改数据源（id 走 query 参数；password 留空表示沿用库中原密码，其余字段按入参覆盖，传空即清空该列）")
    @PutMapping
    public RestResult<DatasourceConnectionItemVO> update(
            @RequestParam @Parameter(description = "数据源 id") Integer id,
            @RequestBody @Valid DatasourceConnectionParam param) {
        return RestResult.ofData(datasourceConnectionService.updateDatasource(id, param));
    }

    @Operation(summary = "删除数据源（物理删除；只是临时下架请改用 status=0，Agent 侧只扫启用的数据源）")
    @DeleteMapping
    public RestResult<Void> delete(@RequestParam @Parameter(description = "数据源 id") Integer id) {
        datasourceConnectionService.deleteDatasource(id);
        return RestResult.success();
    }

    @Operation(summary = "测试连接（只建一次连接读库名与版本，不执行 SQL、不落库；"
            + "传 id 时以库中该条为底、表单里非空字段覆盖，编辑场景可以不带密码）")
    @PostMapping("/test")
    public RestResult<String> test(
            @RequestParam(required = false) @Parameter(description = "已保存的数据源 id，可不传") Integer id,
            @RequestBody(required = false) DatasourceConnectionParam param) {
        return RestResult.ofData(datasourceConnectionService.testConnection(id, param));
    }
}
