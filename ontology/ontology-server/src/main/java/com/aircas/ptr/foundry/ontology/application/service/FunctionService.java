package com.aircas.ptr.foundry.ontology.application.service;


import com.aircas.ptr.foundry.ontology.Exception.FunctionClassNotNewInstanceException;
import com.aircas.ptr.foundry.ontology.Exception.FunctionFileNotCompiled;
import com.aircas.ptr.foundry.ontology.Exception.FunctionNotFoundException;
import com.aircas.ptr.foundry.ontology.Exception.FunctionRuntimeException;
import com.aircas.ptr.foundry.ontology.entity.bo.FunctionBo;
import com.aircas.ptr.foundry.ontology.entity.vo.FunctionVO;
import com.aircas.ptr.foundry.ontology.entity.vo.ParameterMetadataVO;

import java.util.HashMap;
import java.util.List;

public interface FunctionService {
    Object handle(String functionName, Boolean isPreview, String objectTypes, HashMap<String, Object> parameters) throws FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionRuntimeException, FunctionNotFoundException;

    List<ParameterMetadataVO> getParameters(String functionName, Boolean isPreview, String objectTypes) throws FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionNotFoundException;

    Boolean write(String functionName, String code, Boolean isPreview);

    String get(String functionName, Boolean isPreview);

    int saveFunctionMetadata(FunctionBo function);

    int updateFunctionMetadata(FunctionBo functionBo);

    List<FunctionVO> functionMetadataList();

    FunctionVO getFunctionByApi(String api);

    Boolean delete(String functionName);
}
