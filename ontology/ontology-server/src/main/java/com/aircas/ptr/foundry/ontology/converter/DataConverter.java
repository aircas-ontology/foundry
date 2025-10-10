package com.aircas.ptr.foundry.ontology.converter;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.common.constant.Status;
import com.aircas.ptr.foundry.common.constant.Visibility;
import com.aircas.ptr.foundry.common.util.IdGenerator;
import com.aircas.ptr.foundry.ontology.common.param.DataSourceColumnParam;
import com.aircas.ptr.foundry.ontology.common.param.DataSourceParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyDataSourceColumnParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyDatasourceParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaInfoVO;
import lombok.var;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Arrays;
import java.util.stream.Collectors;

public class DataConverter {

    public static OntologyProperty convert(OntologyDataSourceColumnParam param) {
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
                .build();
    }

    public static OntologyMetaInfoVO convert(OntologyMeta ontologyMeta) {
        return OntologyMetaInfoVO.builder()
                .uniqueIdentifier(ontologyMeta.getUniqueIdentifier())
                .apiName(ontologyMeta.getApiName())
                .createTime(ontologyMeta.getCreateTime())
                .updateTime(ontologyMeta.getUpdateTime())
                .description(ontologyMeta.getDescription())
                .icon(ontologyMeta.getIcon())
                .metaGroupId(Arrays.stream(ontologyMeta.getMetaGroupId().split(",")).collect(Collectors.toSet()))
                .displayName(ontologyMeta.getDisplayName())
                .build();
    }


    public static DataSourceParam convert(OntologyDatasourceParam param, String tableName) {

        if (param == null || CollectionUtils.isEmpty(param.getColumnParamList())) {
            return null;
        }

        var columnParamList = param.getColumnParamList().stream().map(v -> DataSourceColumnParam.builder()
                .columnName(v.getApiName())
                .columnType(v.getDatasourceColumnType().getValue())
                .description(v.getDescription())
                .isAssociateKey(v.getIsAssociateKey())
                .isPrimaryKey(v.getIsPrimaryKey())
                .associateDatasourceColumnName(v.getAssociateDatasourceColumnName())
                .tableName(tableName)
                .datasourceId(v.getDatasourceId())
                .datasourceColumnName(v.getDatasourceColumnName())
                .build())
                .collect(Collectors.toList());

        return DataSourceParam.builder()
                .columnParamList(columnParamList)
                .build();
    }
}
