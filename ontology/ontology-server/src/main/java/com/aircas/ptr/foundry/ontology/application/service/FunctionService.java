package com.aircas.ptr.foundry.ontology.application.service;


import com.aircas.ptr.foundry.ontology.entity.bo.FunctionBo;
import com.aircas.ptr.foundry.ontology.entity.vo.FunctionVO;

import java.util.HashMap;
import java.util.List;

public interface FunctionService {
    Object handle(String functionName, Boolean isPreview, String objectTypes, HashMap<String, Object> parameters);

    Boolean write(String functionName, String code, Boolean isPreview);

    String get(String functionName, Boolean isPreview);

    int saveFunctionMetadata(FunctionBo function);

    List<FunctionVO> functionMetadataList();

    FunctionVO getFunctionByApi(String api);

    Boolean delete(String functionName);
}
