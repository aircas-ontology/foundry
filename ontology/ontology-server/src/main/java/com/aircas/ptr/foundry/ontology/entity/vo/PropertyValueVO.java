package com.aircas.ptr.foundry.ontology.entity.vo;


import lombok.Data;

@Data
public class PropertyValueVO {

    private int isTitleKey;

    private int isPrimaryKey;

    private String displayName;

    private String description;

    private String propertyType;

    private String apiName;

    private String value;

    private String uniqueIdentifier;
}
