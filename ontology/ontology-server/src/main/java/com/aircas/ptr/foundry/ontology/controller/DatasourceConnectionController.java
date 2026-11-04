package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.controller.validator.SpaceIdVerify;
import com.aircas.ptr.foundry.ontology.model.param.DatasourceConnectionParam;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceConnectionItemVO;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceSchemaVO;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceTableDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.DatasourceTableSummaryVO;
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

import java.util.List;

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

    @Operation(summary = "列出该对象空间对应数据源下的 schema 清单（按厂商分派：MySQL/MariaDB 走 getCatalogs()，其余走 getSchemas()；"
            + "系统 schema 已过滤）；「数据源构建对象」向导选 schema 步骤用")
    @GetMapping("/schema/list")
    public RestResult<List<DatasourceSchemaVO>> listSchemas(
            @RequestParam @SpaceIdVerify @Parameter(description = "对象空间 id") Integer spaceId) {
        return RestResult.ofData(datasourceConnectionService.listSchemas(spaceId));
    }

    @Operation(summary = "列出该对象空间对应数据源下的表：表名 / 表注释 / 字段数；「数据源构建对象」向导选表步骤用，"
            + "与查数据湖空间内表的 /datasource/table 无关")
    @GetMapping("/table/list")
    public RestResult<List<DatasourceTableSummaryVO>> listTables(
            @RequestParam @SpaceIdVerify @Parameter(description = "对象空间 id") Integer spaceId,
            @RequestParam @Parameter(description = "外部库 schema 名（MySQL/MariaDB 上作为 catalog；其余厂商作为 schema 参数）") String schemaName) {
        return RestResult.ofData(datasourceConnectionService.listTablesWithColumnCount(spaceId, schemaName));
    }

    @Operation(summary = "列出该对象空间对应数据源下多张表的列与主子表外键候选；外键只给本次传入表之间的关系，"
            + "每张子表至多一条，origin=HEURISTIC 的是按同名列推断的、需要用户确认")
    @GetMapping("/table/columns")
    public RestResult<List<DatasourceTableDetailVO>> listTableColumns(
            @RequestParam @SpaceIdVerify @Parameter(description = "对象空间 id") Integer spaceId,
            @RequestParam @Parameter(description = "外部库 schema 名（MySQL/MariaDB 上作为 catalog；其余厂商作为 schema 参数）") String schemaName,
            @RequestParam @Parameter(description = "表名，逗号分隔，区分大小写") List<String> tableNames) {
        return RestResult.ofData(datasourceConnectionService.listTablesDetail(spaceId, schemaName, tableNames));
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
