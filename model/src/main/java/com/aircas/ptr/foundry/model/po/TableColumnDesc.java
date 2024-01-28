package com.aircas.ptr.foundry.model.po;

import lombok.Data;

import java.io.Serializable;

@Data
public class TableColumnDesc implements Serializable {

    private String columnName;

    private String description;

    private String type;
}