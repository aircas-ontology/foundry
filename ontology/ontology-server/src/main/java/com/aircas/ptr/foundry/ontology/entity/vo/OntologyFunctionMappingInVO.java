package com.aircas.ptr.foundry.ontology.entity.vo;

import com.aircas.ptr.foundry.model.po.OntologyFunctionMappingIn;
import lombok.Data;

@Data
public class OntologyFunctionMappingInVO extends OntologyFunctionMappingIn {

    private String propertyName;
    private String propertyType;
    private String parameterType;
}
