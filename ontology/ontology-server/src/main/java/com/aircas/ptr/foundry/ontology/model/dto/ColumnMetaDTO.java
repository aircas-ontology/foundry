package com.aircas.ptr.foundry.ontology.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据源中的字段元数据（内部使用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColumnMetaDTO {

    /**
     * 字段名
     */
    private String columnName;

    /**
     * 字段数据类型，如 bigint / varchar(64)
     */
    private String dataType;

    /**
     * 字段注释（可能为 null）
     */
    private String columnComment;

    /**
     * 是否为主键
     */
    private Boolean primaryKey;

    /**
     * 字段序号，用于稳定排序
     */
    private Integer ordinal;
}
