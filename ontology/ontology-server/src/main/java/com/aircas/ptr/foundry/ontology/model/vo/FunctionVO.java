package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.po.Function;
import lombok.Data;

import java.util.List;


@Data
public class FunctionVO extends Function {
    private List<OntologyMetaVO> ontologyList;
}
