package com.aircas.ptr.foundry.ontology.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据源中的表元数据（内部使用，不直接对外）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableMetaDTO {

    /**
     * schema 名
     */
    private String schemaName;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 表注释（可能为 null）
     */
    private String tableComment;
}
