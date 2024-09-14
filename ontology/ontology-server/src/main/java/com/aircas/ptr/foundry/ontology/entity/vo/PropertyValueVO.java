package com.aircas.ptr.foundry.ontology.entity.vo;


import com.aircas.ptr.foundry.model.po.OntologyDataType;
import lombok.Data;

@Data
public class PropertyValueVO {

    private int isTitleKey;

    private int isPrimaryKey;

    private String displayName;

    private String description;

    private OntologyDataType propertyType;

    private String apiName;

    private Object value;

    private String uniqueIdentifier;
}
