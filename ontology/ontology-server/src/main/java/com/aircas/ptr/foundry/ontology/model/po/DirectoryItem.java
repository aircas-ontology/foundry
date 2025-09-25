package com.aircas.ptr.foundry.ontology.model.po;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;


@Data
public class DirectoryItem implements Serializable {

    private String primaryKey;
    private String displayName;

    private static final long serialVersionUID = 1L;
}