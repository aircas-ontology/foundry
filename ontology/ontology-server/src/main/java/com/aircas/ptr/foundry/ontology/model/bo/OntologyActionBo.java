package com.aircas.ptr.foundry.ontology.model.bo;

import com.aircas.ptr.foundry.model.po.OntologyAction;
import lombok.Data;

import java.util.List;


@Data
public class OntologyActionBo extends OntologyAction {

    //mapping的列表
    private List<OntologyActionMappingInBO> mappingIns;

}
