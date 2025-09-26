package com.aircas.ptr.foundry.ontology.entity.converter;

import com.aircas.ptr.foundry.ontology.entity.model.dto.FieldDTO;
import com.aircas.ptr.foundry.ontology.entity.model.param.DataSourceColumnParam;
import com.aircas.ptr.foundry.ontology.entity.model.po.EntityPropertyPO;

public class ParamDtoConverter {

    public static FieldDTO convert(DataSourceColumnParam param){
        return FieldDTO.builder()
                .fieldComment(param.getDescription())
                .fieldName(param.getColumnName())
                .fieldType(param.getColumnType())
                .isNullable(param.getIsPrimaryKey() ? false : true)
                .isPrimaryKey(param.getIsPrimaryKey())
                .datasourceColumnName(param.getDatasourceColumnName())
                .datasourceId(param.getDatasourceId())
                .tableName(param.getTableName())
                .build();
    }

    public static EntityPropertyPO convertToEntityProperty(FieldDTO v){
        return EntityPropertyPO.builder()
                .tableName(v.getTableName())
                .tableColumnName(v.getFieldName())
                .datasourceColumnName(v.getDatasourceColumnName())
                .datasourceId(v.getDatasourceId())
                .build();
    }
}
