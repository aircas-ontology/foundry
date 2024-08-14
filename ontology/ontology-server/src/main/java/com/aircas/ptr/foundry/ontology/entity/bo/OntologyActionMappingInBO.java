package com.aircas.ptr.foundry.ontology.entity.bo;

import com.aircas.ptr.foundry.model.po.OntologyActionMappingIn;
import lombok.Data;

@Data
public class OntologyActionMappingInBO extends OntologyActionMappingIn {

    boolean isBindToCurrentObject() {
        return this.getPropertyUniqueIdentifier() == "-1";
    }
}
