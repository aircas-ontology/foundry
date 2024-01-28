package com.aircas.ptr.foundry.ontology.entity.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;


@Data
public class TableColumnDescVO {

    private String columnName;

    private String description;

    private String type;
}
