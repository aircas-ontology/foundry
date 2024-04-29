package com.aircas.ptr.foundry.ontology.entity.vo;

import com.aircas.ptr.foundry.model.po.OntologyFunction;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyFunctionMappingInBO;
import lombok.Data;

import java.util.List;


@Data
public class OntologyFunctionVO extends OntologyFunction {
    //mapping的列表
    private List<OntologyFunctionMappingInVO> mappingInList;

    private String ontologyDisplayName;
}
