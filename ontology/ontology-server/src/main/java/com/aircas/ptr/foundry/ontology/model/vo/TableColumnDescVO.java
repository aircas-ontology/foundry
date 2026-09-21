package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;


/**
 * 表列信息 VO（列名 + 列描述 + 数据类型 + 是否主键）。
 *
 * <p>两条链路共用同一结构：</p>
 * <ul>
 *   <li>{@code TableMetadataService#getColumns} —— 按 spaceId 查本体空间<b>已登记的元数据</b>（走 XML Mapper），供前端 REST 展示；</li>
 *   <li>{@code DatasourceMcpTools#scanTableColumns} —— 通过 JDBC {@code DatabaseMetaData#getColumns} <b>实时扫描外部数据源</b>，供 Agent 生成对象属性。</li>
 * </ul>
 *
 * <p>字段名即 MCP/JSON 序列化后的对外契约（{@code columnName} / {@code description} / {@code type} / {@code isPrimaryKey}），
 * Agent 提示词按此取值，修改字段名须同步 {@code ChatClientConfig} 功能三。</p>
 */
@Data
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "表列信息 VO（列名/列描述/数据类型/是否主键；REST 元数据查询与 MCP 实时扫描共用）")
public class TableColumnDescVO {

    @Schema(name = "columnName",description = "列名")
    private String columnName;

    @Schema(name = "description",description = "列描述信息")
    private String description;

    @Schema(name = "type",description = "列数据类型")
    private String type;

    @Schema(name = "isPrimaryKey",description = "是否为主键")
    private Boolean isPrimaryKey;
}
