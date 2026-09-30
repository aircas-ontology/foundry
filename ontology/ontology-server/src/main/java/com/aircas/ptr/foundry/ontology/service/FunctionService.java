package com.aircas.ptr.foundry.ontology.service;


import com.aircas.ptr.foundry.ontology.model.param.FunctionCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionVersionParam;
import com.aircas.ptr.foundry.ontology.model.po.Function;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionExecuteResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionVersionVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.fasterxml.jackson.databind.JsonNode;

public interface FunctionService extends IService<Function> {


    Page<FunctionInfoVO> getFunctions(Integer pageNum, Integer pageSize);

    String executeFunction(FunctionExecuteParam param);


    FunctionDetailVO getFunctionDetailByApi(String api);

    FunctionDetailVO getFunctionDetailByApi(String api, Integer versionNo);

    void deleteByApi(String api);


    void createFunction(FunctionCreateParam param);

    void updateFunction(FunctionUpdateParam param);

    FunctionExecuteResultVO getExecuteResult(String taskId);

    void callback(FunctionResultVO result);

    void createDraft(FunctionVersionParam param);

    void publishVersion(FunctionVersionParam param);

    void rollbackVersion(FunctionVersionParam param);

    Page<FunctionVersionVO> listVersions(String functionApi, Integer pageNum, Integer pageSize);
}
