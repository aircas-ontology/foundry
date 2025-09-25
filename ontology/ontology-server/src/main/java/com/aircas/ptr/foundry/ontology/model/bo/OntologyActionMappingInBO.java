package com.aircas.ptr.foundry.ontology.model.bo;

import com.aircas.ptr.foundry.ontology.model.po.OntologyActionMappingIn;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Objects;

import static com.aircas.ptr.foundry.common.constant.ActionMappingInTypeEnum.ONTOLOGY;

/**
 * @author Lenovo
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class OntologyActionMappingInBO extends OntologyActionMappingIn {

    boolean isBindToCurrentObject() {

        return Objects.equals(this.getPropertyUniqueIdentifier(), ONTOLOGY.getCode());
    }
}
