package com.aircas.ptr.foundry.ontology.converter;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.common.constant.Status;
import com.aircas.ptr.foundry.common.constant.Visibility;
import com.aircas.ptr.foundry.common.util.IdGenerator;
import com.aircas.ptr.foundry.ontology.model.param.OntologyDataSourceColumnParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;

public class ParamToEntityConverter {

    public static OntologyProperty convert(OntologyDataSourceColumnParam param, String ontologyUniqueIdentifier) {
        return OntologyProperty.builder()
                .status(Status.ENABLE.getValue())
                .datasourceColumnName(param.getDatasourceColumnName())
                .propertyType(OntologyDataTypeEnum.valueOfPg(param.getDatasourceColumnType().getValue()))
                .displayName(param.getDisplayName())
                .description(param.getDescription())
                .apiName(param.getApiName())
                .datasourceId(param.getDatasourceId())
                .isPrimaryKey(param.getIsPrimaryKey() ? 1 : 0)
                .isTitleKey(param.getIsTitleKey() ? 1 : 0)
                .uniqueIdentifier(IdGenerator.generateUUID())
                .visibility(Visibility.NORMAL.getValue())
                .isAssociateKey(param.getIsAssociateKey() ? 1 : 0)
                .associateDatasourceColumnName(param.getAssociateDatasourceColumnName())
                .ontologyUniqueIdentifier(ontologyUniqueIdentifier)
                .build();
    }
}
