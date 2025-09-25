package com.aircas.ptr.foundry.ontology.entity.model.po;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
public class EntityPropertyMappingPO extends BasePO {

    private String entityTable;

    private String entityPropertyTable;

    private String entityTableKey;

    private String entityPropertyTableKey;

}
