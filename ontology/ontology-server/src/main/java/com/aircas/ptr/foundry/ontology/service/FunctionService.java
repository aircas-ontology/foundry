package com.aircas.ptr.foundry.ontology.service;


import com.aircas.ptr.foundry.ontology.exception.FunctionClassNotNewInstanceException;
import com.aircas.ptr.foundry.ontology.exception.FunctionFileNotCompiled;
import com.aircas.ptr.foundry.ontology.exception.FunctionNotFoundException;
import com.aircas.ptr.foundry.ontology.exception.FunctionRuntimeException;
import com.aircas.ptr.foundry.ontology.model.bo.FunctionBo;
import com.aircas.ptr.foundry.ontology.model.param.FunctionCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.Function;
import com.aircas.ptr.foundry.ontology.model.view.FunctionView;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionVO;
import com.aircas.ptr.foundry.ontology.model.vo.ParameterMetadataVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.HashMap;
import java.util.List;

public interface FunctionService extends IService<Function> {


    Object handle(String functionName, Boolean isPreview, List<String> objectTypes, HashMap<String, Object> parameters) throws FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionRuntimeException, FunctionNotFoundException;

    List<ParameterMetadataVO> getParameters(String functionName) throws FunctionClassNotNewInstanceException, FunctionFileNotCompiled, FunctionNotFoundException;

    int saveFunctionMetadata(FunctionBo function);

    int updateFunctionMetadata(FunctionBo functionBo);

    List<FunctionVO> functionMetadataList();

    Page<FunctionInfoVO> getFunctions(Integer pageNum, Integer pageSize);

    String executeFunction(FunctionExecuteParam param);

    FunctionVO getFunctionByApi(String api);

    FunctionDetailVO getFunctionDetailByApi(String api);

    void deleteByApi(String api);

    FunctionVO queryById(Long id);

    Boolean deleteById(Long id);

    int getCountByStatus(int status);

    void createFunction(FunctionCreateParam param);

    void updateFunction(FunctionUpdateParam param);

    List<FunctionView> queryFunctionViewByOntologyId(String ontologyUniqId);
}
