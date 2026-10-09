package com.aircas.ptr.foundry.ontology.service;


import com.aircas.ptr.foundry.ontology.model.param.FunctionCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionUpdateParam;
import com.aircas.ptr.foundry.ontology.model.po.Function;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionExecuteResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionVersionVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionVersionCreatedVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Collection;
import java.util.Map;

public interface FunctionService extends IService<Function> {


    Page<FunctionInfoVO> getFunctions(Integer pageNum, Integer pageSize);

    String executeFunction(FunctionExecuteParam param);


    FunctionDetailVO getFunctionDetailByApi(String api, Long functionVersionId);

    void deleteByApi(String api);


    FunctionVersionCreatedVO createFunction(FunctionCreateParam param);

    FunctionVersionCreatedVO createDraftFunction(FunctionCreateParam param);

    void updateFunction(FunctionUpdateParam param);

    FunctionExecuteResultVO getExecuteResult(String taskId);

    void callback(FunctionResultVO result);

    Page<FunctionVersionVO> listVersions(String functionApi, Integer pageNum, Integer pageSize);

    /**
     * 按 function api 批量获取最新已发布版本的函数描述。
     * <p>
     * 空白 api、不存在的 api 或描述为空的函数不会出现在返回结果中。
     *
     * @param functionApis 函数 api 集合，允许为空
     * @return {@code functionApi -> description} 映射；无数据时返回空 Map
     */
    Map<String, String> mapDescriptionByApi(Collection<String> functionApis);
}
