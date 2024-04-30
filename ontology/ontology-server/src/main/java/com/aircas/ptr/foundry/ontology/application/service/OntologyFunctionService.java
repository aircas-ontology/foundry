package com.aircas.ptr.foundry.ontology.application.service;


import com.aircas.ptr.foundry.ontology.Exception.*;
import com.aircas.ptr.foundry.ontology.entity.bo.FunctionRequestBodyBO;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyFunctionBo;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyFunctionVO;
import com.aircas.ptr.foundry.ontology.entity.vo.ParameterMetadataVO;

import java.util.List;

public interface OntologyFunctionService {

    int save(OntologyFunctionBo ontologyFunctionBo) throws OntologyFunctionParameterPropertyTypeNotSameException, FunctionFileNotCompiled, FunctionNotFoundException, FunctionClassNotNewInstanceException, OntologyFunctionBindingParameterNotFoundException, OntologyFunctionMappedPropertyNotFoundException;

    List<ParameterMetadataVO> getParameters(String functionApi, String ontologyUniqueIdentifier, boolean isPreview) throws FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionNotFoundException, OntologyFunctionNotFoundException;

    OntologyFunctionVO getMetadata(String functionApi, String ontologyUniqueIdentifier, boolean isPreview) throws OntologyFunctionNotFoundException, OntologyFunctionMappedPropertyNotFoundException, FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionNotFoundException;

    List<OntologyFunctionVO> queryByOntologyUniqueIdentifier(String ontologyUniqueIdentifier) throws OntologyFunctionMappedPropertyNotFoundException, FunctionFileNotCompiled, FunctionNotFoundException, FunctionClassNotNewInstanceException;

    Object handle(FunctionRequestBodyBO functionRequestBodyBO) throws FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionRuntimeException, FunctionNotFoundException, OntologyFunctionNotFoundException, OntologyApiNameNotFoundException;

    int delete(long id);
}
