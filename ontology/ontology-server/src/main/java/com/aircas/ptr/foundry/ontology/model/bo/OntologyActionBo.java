package com.aircas.ptr.foundry.ontology.model.bo;

import com.aircas.ptr.foundry.ontology.model.po.OntologyAction;
import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.util.List;


@Data
@SuperBuilder
public class OntologyActionBo extends OntologyAction {

    //mapping的列表
    private List<OntologyActionMappingInBO> mappingIns;

}
