package com.aircas.ptr.foundry.ontology.converter;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.common.constant.Status;
import com.aircas.ptr.foundry.common.util.IdGenerator;
import com.aircas.ptr.foundry.ontology.common.param.EntityDataSourceColumnParam;
import com.aircas.ptr.foundry.ontology.common.param.EntityDataSourceParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyDataSourceColumnParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPrimaryDatasourceParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyCreateParamV2;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyMetaInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyPropertyDetailVO;
import lombok.var;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Arrays;
import java.util.stream.Collectors;

public class DataConverter {

    public static OntologyProperty convert(OntologyPropertyCreateParamV2 param) {
        var datasource = param.getDatasource();

        var prop = OntologyProperty.builder()
                .uniqueIdentifier(IdGenerator.generateUUID())
                .tag(param.getTag())
                .status(Status.ENABLE.getValue())
                .propertyType(param.getDataType())
                .isTitleKey(param.getIsTitleKey() ? 1 : 0)
                .isPrimaryKey(param.getIsPrimaryKey() ? 1 : 0)
                .displayName(param.getDisplayName())
                .description(param.getDescription())
                .apiName(param.getApiName())
                .ontologyUniqueIdentifier(param.getOntologyIdentifier())
                .build();
        if (datasource != null) {
            prop.setDatasourceId(datasource.getDatasourceId())
                    .setDatasourceColumnName(datasource.getDatasourceColumnName());
        }
        return prop;
    }

    public static OntologyPropertyDetailVO convert(OntologyProperty p) {
        return OntologyPropertyDetailVO.builder()
                .apiName(p.getApiName())
                .datasourceColumnName(p.getDatasourceColumnName())
                .datasourceId(p.getDatasourceId())
                .description(p.getDescription())
                .displayName(p.getDisplayName())
                .isPrimaryKey(p.getIsPrimaryKey() == 1)
                .isTitleKey(p.getIsTitleKey() == 1)
                .propertyType(p.getPropertyType())
                .tag(p.getTag())
                .uniqueIdentifier(p.getUniqueIdentifier())
                .build();
    }

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
                .tag(param.getTag())
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

    public static EntityDataSourceParam convert(OntologyPrimaryDatasourceParam param, String tableName) {

        if (param == null || CollectionUtils.isEmpty(param.getColumnParamList())) {
            return null;
        }

        var columnParamList = param.getColumnParamList().stream().map(v -> EntityDataSourceColumnParam.builder()
                .columnName(v.getApiName())
                .columnType(v.getDatasourceColumnType().getValue())
                .description(v.getDescription())
                .isAssociateKey(v.getIsAssociateKey())
                .isPrimaryKey(v.getIsPrimaryKey())
                .isTitleKey(v.getIsTitleKey())
                .associateDatasourceColumnName(v.getAssociateDatasourceColumnName())
                .tableName(tableName)
                .datasourceId(v.getDatasourceId())
                .datasourceColumnName(v.getDatasourceColumnName())
                .build())
                .collect(Collectors.toList());

        return EntityDataSourceParam.builder()
                .columnParamList(columnParamList)
                .build();
    }

    public static EntityDataSourceColumnParam convertEntityDataSource(OntologyDataSourceColumnParam param) {
        return EntityDataSourceColumnParam.builder()
                .columnName(param.getApiName())
                .columnType(param.getDatasourceColumnType().getValue())
                .datasourceColumnName(param.getDatasourceColumnName())
                .datasourceId(param.getDatasourceId())
                .isAssociateKey(param.getIsAssociateKey())
                .associateDatasourceColumnName(param.getAssociateDatasourceColumnName())
                .description(param.getDescription())
                .isPrimaryKey(param.getIsPrimaryKey())
                .isTitleKey(param.getIsTitleKey())
                .build();
    }
}
