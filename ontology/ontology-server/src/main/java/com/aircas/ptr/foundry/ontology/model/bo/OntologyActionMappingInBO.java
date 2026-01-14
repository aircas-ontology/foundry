package com.aircas.ptr.foundry.ontology.model.bo;

import com.aircas.ptr.foundry.ontology.model.po.OntologyActionMappingIn;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Objects;

import static com.aircas.ptr.foundry.ontology.model.enums.ActionMappingInTypeEnum.ONTOLOGY;

/**
 * @author Lenovo
 */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
public class OntologyActionMappingInBO extends OntologyActionMappingIn {

    boolean isBindToCurrentObject() {

        return Objects.equals(this.getPropertyUniqueIdentifier(), ONTOLOGY.getCode());
    }
}
