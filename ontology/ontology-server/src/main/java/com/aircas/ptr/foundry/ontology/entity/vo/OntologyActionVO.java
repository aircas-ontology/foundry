package com.aircas.ptr.foundry.ontology.entity.vo;

import com.aircas.ptr.foundry.model.po.OntologyAction;
import lombok.Data;

import java.util.List;


@Data
public class OntologyActionVO extends OntologyAction {

    //mapping的列表
    private List<OntologyActionMappingInVO> mappingIns;

    private String ontologyDisplayName;
}
