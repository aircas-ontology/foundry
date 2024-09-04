package com.aircas.ptr.foundry.rule.executor.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Objects;

import static com.aircas.ptr.foundry.common.constant.ActionMappingInTypeEnum.ONTOLOGY;

/**
 * @author Lenovo
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class OntologyActionMappingIn extends com.aircas.ptr.foundry.model.po.OntologyActionMappingIn {

    boolean isBindToCurrentObject() {

        return Objects.equals(this.getPropertyUniqueIdentifier(), ONTOLOGY.getCode());
    }
}
