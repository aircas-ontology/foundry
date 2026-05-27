package com.aircas.ptr.foundry.ontology.model.po;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Builder
@Accessors(chain = true)
public class TableDesc implements Serializable {

    private String tableName;

    private String description;

}