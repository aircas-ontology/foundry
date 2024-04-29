package com.aircas.ptr.foundry.ontology.application.service;


import com.aircas.ptr.foundry.ontology.Exception.*;
import com.aircas.ptr.foundry.ontology.entity.bo.FunctionRequestBodyBO;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyFunctionBo;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyFunctionVO;
import com.aircas.ptr.foundry.ontology.entity.vo.ParameterMetadataVO;

import java.util.List;

public interface OntologyFunctionService {

    int save(OntologyFunctionBo ontologyFunctionBo);

    List<ParameterMetadataVO> getParameters(String functionApi, String ontologyUniqueIdentifier) throws FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionNotFoundException, OntologyFunctionNotFoundException;

    int delete(long id);

    List<OntologyFunctionVO> queryByOntologyUniqueIdentifier(String ontologyUniqueIdentifier);

    Object handle(FunctionRequestBodyBO functionRequestBodyBO) throws FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionRuntimeException, FunctionNotFoundException;
}
