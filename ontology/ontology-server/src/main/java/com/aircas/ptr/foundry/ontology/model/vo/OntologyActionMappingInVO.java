package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.po.OntologyActionMappingIn;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class OntologyActionMappingInVO extends OntologyActionMappingIn {

    private String propertyName;
    private String propertyType;
    private String parameterType;
}
