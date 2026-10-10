package com.aircas.ptr.foundry.ontology.service;


import com.aircas.ptr.foundry.ontology.model.param.FunctionCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionUpdateParam;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionExecuteResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionResultVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public interface FunctionService {


    Page<FunctionInfoVO> getFunctions(Integer pageNum, Integer pageSize);

    String executeFunction(FunctionExecuteParam param);


    FunctionDetailVO getFunctionDetailByApi(String api);

    void deleteByApi(String api);


    void createFunction(FunctionCreateParam param);

    void updateFunction(FunctionUpdateParam param);

    FunctionExecuteResultVO getExecuteResult(String taskId);

    void callback(FunctionResultVO result);


    /**
     * 按 function api 批量取函数描述，供行为出参冗余函数描述使用（一次查询，避免逐条查库）。
     * <p>
     * api 不存在或描述为空的不会出现在返回结果中。
     *
     * @param functionApis 函数 api 列表，允许为空
     * @return {@code functionApi -> description} 映射；无数据时返回空 Map，不返回 null
     */
    Map<String, String> mapDescriptionByApi(List<String> functionApis);
}
