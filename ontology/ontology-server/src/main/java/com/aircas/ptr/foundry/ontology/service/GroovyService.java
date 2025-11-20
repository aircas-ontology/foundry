package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.dto.FunctionParamDTO;
import com.aircas.ptr.foundry.ontology.model.param.Parameter;
import com.aircas.ptr.foundry.ontology.model.po.FunctionParamPO;

import java.util.List;

public interface GroovyService {

    List<FunctionParamDTO> parseFunctionParam(String code);

    void executeGroovy(String code, List<Parameter> parameters, List<FunctionParamPO> paramInfos);

}
