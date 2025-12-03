package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.dto.FunctionParamDTO;
import com.aircas.ptr.foundry.ontology.model.po.FunctionParamPO;

import java.util.List;
import java.util.Map;

public interface GroovyService {

    /**
     * 解析groovy代码片段，获取handle方法参数列表及返回值信息
     *
     * @param code
     * @return
     */

    List<FunctionParamDTO> parseGroovyCode(String code);


    /**
     * 编译groovy代码片段，动态执行handle方法
     *
     * @param code
     * @param paramMap
     * @param executeInputParams
     * @return
     */
    String executeGroovy(String code, Map<String, Object> paramMap, List<FunctionParamPO> executeInputParams);
}
