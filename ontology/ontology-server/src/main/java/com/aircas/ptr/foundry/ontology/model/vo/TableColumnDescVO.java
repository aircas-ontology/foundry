package com.aircas.ptr.foundry.ontology.model.vo;

import lombok.Data;


@Data
public class TableColumnDescVO {

    private String columnName;

    private String description;

    private String type;

    private Boolean isPrimaryKey;
}
