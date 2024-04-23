package com.aircas.ptr.foundry.ontology.entity.bo;

import com.aircas.ptr.foundry.model.po.OntologyFunctionMappingIn;
import lombok.Data;

@Data
public class OntologyFunctionMappingInBO extends OntologyFunctionMappingIn {

    boolean isBindToCurrentObject() {
        return this.getPropertyUniqueIdentifier() == "-1";
    }
}
