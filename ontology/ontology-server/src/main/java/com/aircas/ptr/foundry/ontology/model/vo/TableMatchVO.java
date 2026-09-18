package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 单张匹配到的表信息
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Schema(description = "匹配到的表信息")
public class TableMatchVO {

    @Schema(name = "tableName", description = "表名", example = "satellite")
    private String tableName;

    @Schema(name = "schemaName", description = "所属 schema", example = "public")
    private String schemaName;

    @Schema(name = "description", description = "表的中文描述（来自数据库注释）", example = "卫星基础信息表")
    private String description;

    @Schema(name = "mainTable", description = "是否为 LLM 选中的主表；false 表示通过外键关联扩展进来")
    private Boolean mainTable;

    @Schema(name = "relation", description = "与主表的关联关系描述，主表本身为 null",
            example = "satellite.orbit_id -> orbit.id")
    private String relation;

    @Schema(name = "columns", description = "字段列表（含中文描述）")
    private List<ColumnMatchVO> columns;
}
