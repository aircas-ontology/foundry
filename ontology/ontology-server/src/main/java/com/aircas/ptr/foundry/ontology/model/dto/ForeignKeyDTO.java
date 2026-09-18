package com.aircas.ptr.foundry.ontology.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 外键关系元数据（内部使用）
 * 表示 fromTable.fromColumn -> toTable.toColumn
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForeignKeyDTO {

    private String fromSchema;
    private String fromTable;
    private String fromColumn;

    private String toSchema;
    private String toTable;
    private String toColumn;
}
