package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.dto.FunctionParamDTO;

import java.util.List;

public interface GroovyParseService {

    List<FunctionParamDTO> parseFunctionParam(String code);

}
