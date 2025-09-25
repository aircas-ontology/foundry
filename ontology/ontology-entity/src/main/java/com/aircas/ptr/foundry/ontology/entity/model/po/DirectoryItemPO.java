package com.aircas.ptr.foundry.ontology.entity.model.po;

import lombok.Data;

import java.io.Serializable;


@Data
public class DirectoryItemPO implements Serializable {

    private String primaryKey;
    private String displayName;

    private static final long serialVersionUID = 1L;
}