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
     * 编译groovy代码片段，动态执行handle方法。
     * functionApi 作为缓存键；code 来自 DB，变更后会按内容哈希自动重新编译。
     *
     * @param functionApi 函数唯一标识，用于缓存与失效
     * @param code
     * @param paramMap
     * @param executeInputParams
     * @return
     */
    String executeGroovy(String functionApi, String code, Map<String, Object> paramMap, List<FunctionParamPO> executeInputParams);

    /**
     * 函数更新/删除时主动失效编译缓存，避免旧 Class 长期占用 Metaspace。
     */
    void invalidateCompiledClass(String functionApi);
}
