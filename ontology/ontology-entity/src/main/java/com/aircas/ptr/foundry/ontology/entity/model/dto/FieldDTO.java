package com.aircas.ptr.foundry.ontology.entity.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public  class FieldDTO {

    private String datasourceColumnName;

    private String datasourceId;

    private String tableName;

    private String fieldName;

    private String fieldType;

    private String fieldComment;

    private boolean isPrimaryKey;

    private boolean isNullable;

}
