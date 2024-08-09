package com.aircas.ptr.foundry.model.po;

import lombok.Data;

import java.io.Serializable;

@Data
public class DatasourceTable implements Serializable {

    private String tableName;

    private String description;
}
