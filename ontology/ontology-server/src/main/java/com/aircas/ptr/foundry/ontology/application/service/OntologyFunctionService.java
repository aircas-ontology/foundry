package com.aircas.ptr.foundry.ontology.application.service;


import com.aircas.ptr.foundry.ontology.Exception.FunctionClassNotNewInstanceException;
import com.aircas.ptr.foundry.ontology.Exception.FunctionFileNotCompiled;
import com.aircas.ptr.foundry.ontology.Exception.FunctionNotFoundException;
import com.aircas.ptr.foundry.ontology.Exception.FunctionRuntimeException;
import com.aircas.ptr.foundry.ontology.entity.bo.FunctionRequestBodyBO;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyFunctionBo;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyFunctionVO;

import java.util.List;

public interface OntologyFunctionService {

    int save(OntologyFunctionBo ontologyFunctionBo);

    int delete(long id);

    List<OntologyFunctionVO> queryByOntologyUniqueIdentifier(String ontologyUniqueIdentifier);

    Object handle(FunctionRequestBodyBO functionRequestBodyBO) throws FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionRuntimeException, FunctionNotFoundException;
}
