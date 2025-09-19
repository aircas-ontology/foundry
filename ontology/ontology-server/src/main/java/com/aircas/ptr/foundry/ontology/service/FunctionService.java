package com.aircas.ptr.foundry.ontology.service;


import com.aircas.ptr.foundry.ontology.exception.FunctionClassNotNewInstanceException;
import com.aircas.ptr.foundry.ontology.exception.FunctionFileNotCompiled;
import com.aircas.ptr.foundry.ontology.exception.FunctionNotFoundException;
import com.aircas.ptr.foundry.ontology.exception.FunctionRuntimeException;
import com.aircas.ptr.foundry.ontology.model.bo.FunctionBo;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionVO;
import com.aircas.ptr.foundry.ontology.model.vo.ParameterMetadataVO;

import java.util.HashMap;
import java.util.List;

public interface FunctionService {
    Object handle(String functionName, Boolean isPreview, List<String> objectTypes, HashMap<String, Object> parameters) throws FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionRuntimeException, FunctionNotFoundException;

    List<ParameterMetadataVO> getParameters(String functionName) throws FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionNotFoundException;

    Boolean write(String functionName, String code, Boolean isPreview);

    String get(String functionName, Boolean isPreview);

    int saveFunctionMetadata(FunctionBo function);

    int updateFunctionMetadata(FunctionBo functionBo);

    List<FunctionVO> functionMetadataList();

    FunctionVO getFunctionByApi(String api);

    Boolean deleteByApi(String functionName);

    FunctionVO queryById(Long id);

    Boolean deleteById(Long id);

    int getCountByStatus(int status);
}
