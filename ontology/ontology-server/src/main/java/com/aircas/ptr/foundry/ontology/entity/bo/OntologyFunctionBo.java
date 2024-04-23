package com.aircas.ptr.foundry.ontology.entity.bo;

import com.aircas.ptr.foundry.model.po.OntologyFunction;
import com.aircas.ptr.foundry.model.po.OntologyFunctionMappingIn;
import lombok.Data;

import java.util.List;


@Data
public class OntologyFunctionBo extends OntologyFunction {

    //mapping的列表
    private List<OntologyFunctionMappingInBO> mappingInList;

}
