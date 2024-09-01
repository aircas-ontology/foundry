package com.aircas.ptr.foundry.ontology.application.service;


import com.aircas.ptr.foundry.ontology.Exception.*;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyActionBo;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyActionVO;
import com.aircas.ptr.foundry.ontology.entity.vo.ParameterMetadataVO;
import com.aircas.ptr.foundry.ontology.repository.param.ActionHandleMappingInParam;
import com.github.pagehelper.PageInfo;

import java.util.List;

public interface OntologyActionService {

    int save(OntologyActionBo ontologyActionBo) throws OntologyFunctionParameterPropertyTypeNotSameException, FunctionFileNotCompiled, FunctionNotFoundException, FunctionClassNotNewInstanceException, OntologyFunctionBindingParameterNotFoundException, OntologyFunctionMappedPropertyNotFoundException;

    List<ParameterMetadataVO> getParametersByApi(String actionApi) throws FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionNotFoundException, OntologyFunctionNotFoundException;

    OntologyActionVO getMetadataByApi(String apiName) throws OntologyFunctionNotFoundException, OntologyFunctionMappedPropertyNotFoundException, FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionNotFoundException;

    List<OntologyActionVO> queryByOntologyUniqueIdentifier(String ontologyUniqueIdentifier) throws OntologyFunctionMappedPropertyNotFoundException, FunctionFileNotCompiled, FunctionNotFoundException, FunctionClassNotNewInstanceException;

    Object handle(String primaryKey , String api, List<ActionHandleMappingInParam> params) throws FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionRuntimeException, FunctionNotFoundException, OntologyFunctionNotFoundException, OntologyApiNameNotFoundException, OntologyFunctionMappedPropertyNotFoundException;

    int delete(long id);

    PageInfo<OntologyActionVO> metaList(Integer page, Integer size);

    int update(OntologyActionBo ontologyActionBo);

    void handleTask(String api);

    int getCountByStatus(int status);
}
