package com.aircas.ptr.foundry.ontology.converter;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.common.constant.Status;
import com.aircas.ptr.foundry.common.util.IdGenerator;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.param.OntologyDataSourceColumnParam;

public class ParamToEntityConverter {

    public static OntologyProperty convert(OntologyDataSourceColumnParam param){
        return OntologyProperty.builder()
                .status(Status.ENABLE.getValue())
                .datasourceColumnName(param.getColumnName())
                .propertyType(OntologyDataTypeEnum.valueOfPg(param.getColumnType().getValue()))
                .displayName(param.getDisplayName())
                .description(param.getDescription())
                .apiName(param.getApiName())
                .datasourceId(param.getTableName())
                .isPrimaryKey(param.getIsPrimaryKey() ? 1 : 0)
                .isTitleKey(param.getIsTitleKey() ? 1 : 0)
                .uniqueIdentifier(IdGenerator.generateUUID())
                .build();
    }
}
