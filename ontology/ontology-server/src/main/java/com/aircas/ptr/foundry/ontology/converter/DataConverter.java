package com.aircas.ptr.foundry.ontology.converter;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.common.util.DateUtils;
import com.aircas.ptr.foundry.common.util.IdGenerator;
import com.aircas.ptr.foundry.ontology.model.document.EntityRelation;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.model.enums.VisibilityEnum;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyCreateParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkGroup;
import com.aircas.ptr.foundry.ontology.model.po.OntologyMeta;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.google.common.collect.Sets;
import lombok.var;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.stream.Collectors;

public class DataConverter {

    public static EntityLinkPropertyVO convert(EntityRelation relation) {
        return EntityLinkPropertyVO.builder()
                .ontologyFrom(relation.getFrom().getOntologyUniqIdentifier())
                .ontologyTo(relation.getTo().getOntologyUniqIdentifier())
                .entityPrimaryKeyFrom(relation.getFrom().getPrimaryKey())
                .entityPrimaryKeyTo(relation.getTo().getPrimaryKey())
                .displayNameFrom(relation.getFrom().getDisplayName())
                .displayNameTo(relation.getTo().getDisplayName())
                .linkName(relation.getName())
                .linkType(relation.getType())
                .entityNodeFrom(relation.getFrom().getId())
                .entityNodeTo(relation.getTo().getId())
                .startTime(relation.getStartTime())
                .endTime(relation.getEndTime())
                .visibilityWindows(relation.getTimeWindows())
                .status(relation.getStatus())
                .build();
    }


    public static OntologyLinkInfoVO convert(OntologyLinkGroup link, OntologyMeta from, OntologyMeta to) {
        return OntologyLinkInfoVO.builder()
                .name(link.getName())
                .uniqueIdentifier(link.getUniqueIdentifier())
                .ontologyUniqueIdentifierFrom(from.getUniqueIdentifier())
                .ontologyUniqueIdentifierTo(to.getUniqueIdentifier())
                .ontologyIconFrom(from.getIcon())
                .ontologyIconTO(to.getIcon())
                .ontologyNameFrom(from.getDisplayName())
                .ontologyNameTo(to.getDisplayName())
                .type(link.getType())
                .build();
    }


    public static OntologyProperty convert(OntologyPropertyCreateParam param) {
        var datasource = param.getDatasource();
        var prop = OntologyProperty.builder()
                .uniqueIdentifier(IdGenerator.generateUUID())
                .status(Status.ENABLE.getValue())
                .propertyType(param.getDataType())
                .isTitleKey(param.getIsTitleKey() ? 1 : 0)
                .isPrimaryKey(param.getIsPrimaryKey() ? 1 : 0)
                .displayName(param.getDisplayName())
                .description(param.getDescription())
                .apiName(param.getApiName())
                .ontologyUniqueIdentifier(param.getOntologyIdentifier())
                .visibility(VisibilityEnum.NORMAL.getValue())
                .defaultValue(param.getDefaultValue())
                .storageGroup(param.getStorageGroup())
                .propertyCategoryId(param.getCategoryId())
                .metadata(param.getMetadata())
                .build();
        if (datasource != null) {
            prop.setDatasourceId(datasource.getDatasourceId())
                    .setDatasourceSchema(datasource.getSchemaName())
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
                .uniqueIdentifier(p.getUniqueIdentifier())
                .ontologyUniqueIdentifier(p.getOntologyUniqueIdentifier())
                .defaultValue(p.getDefaultValue())
                .storageGroup(p.getStorageGroup())
                .categoryId(p.getPropertyCategoryId())
                .metadata(p.getMetadata())
                .build();
    }

    public static OntologyPropertyInfoVO convertToPropertyInfoVO(OntologyProperty p) {
        return OntologyPropertyInfoVO.builder()
                .description(p.getDescription())
                .displayName(p.getDisplayName())
                .isPrimaryKey(p.getIsPrimaryKey() == 1)
                .isTitleKey(p.getIsTitleKey() == 1)
                .uniqueIdentifier(p.getUniqueIdentifier())
                .ontologyUniqueIdentifier(p.getOntologyUniqueIdentifier())
                .defaultValue(p.getDefaultValue())
                .storageGroup(p.getStorageGroup())
                .categoryId(p.getPropertyCategoryId())
                .metadata(p.getMetadata())
                .build();
    }


    public static OntologyMetaInfoVO convert(OntologyMeta ontologyMeta) {
        return OntologyMetaInfoVO.builder()
                .uniqueIdentifier(ontologyMeta.getUniqueIdentifier())
                .apiName(ontologyMeta.getApiName())
                .createTime(ontologyMeta.getCreateTime())
                .updateTime(ontologyMeta.getUpdateTime())
                .latestQueryTime(ontologyMeta.getLatestQueryTime())
                .description(ontologyMeta.getDescription())
                .icon(ontologyMeta.getIcon())
                .metaGroupId(StringUtils.isEmpty(ontologyMeta.getMetaGroupId()) ? Sets.newHashSet() : Arrays.stream(ontologyMeta.getMetaGroupId().split(",")).collect(Collectors.toSet()))
                .displayName(ontologyMeta.getDisplayName())
                .spaceId(ontologyMeta.getOntologySpaceId())
                .ontologyCategoryId(ontologyMeta.getOntologyCategoryId())
                .parentOntologyUniqueIdentifier(ontologyMeta.getParentUniqueIdentifier())
                .build();
    }


    public static Object convert2DataType(Object inputDataValue, OntologyDataTypeEnum targetDataType) {

        if (inputDataValue == null || targetDataType == null) {
            return null;
        }

        String valueStr = inputDataValue.toString().trim();

        switch (targetDataType) {
            case Timestamp:
                return DateUtils.convertToTimestamp(valueStr, "yyyy-MM-dd HH:mm:ss");
            case Date:
                return DateUtils.fromString2Date(valueStr, "yyyy-MM-dd");
            case Float:
                return Float.parseFloat(valueStr);
            case Double:
                return Double.parseDouble(valueStr);
            case Int:
                return Integer.parseInt(valueStr);
            case Long:
                return Long.parseLong(valueStr);
            default:
                return inputDataValue; // 原样返回不支持的类型
        }
    }


}