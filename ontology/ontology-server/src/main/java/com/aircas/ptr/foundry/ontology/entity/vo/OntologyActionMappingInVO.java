package com.aircas.ptr.foundry.ontology.entity.vo;

import com.aircas.ptr.foundry.model.po.OntologyActionMappingIn;
import lombok.Data;

@Data
public class OntologyActionMappingInVO extends OntologyActionMappingIn {

    private String propertyName;
    private String propertyType;
    private String parameterType;
}
