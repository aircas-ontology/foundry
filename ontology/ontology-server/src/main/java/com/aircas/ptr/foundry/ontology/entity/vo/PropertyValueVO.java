package com.aircas.ptr.foundry.ontology.entity.vo;


import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import lombok.Data;

@Data
public class PropertyValueVO {

    private int isTitleKey;

    private int isPrimaryKey;

    private String displayName;

    private String description;

    private OntologyDataTypeEnum propertyType;

    private String apiName;

    private Object value;

    private String uniqueIdentifier;
}
