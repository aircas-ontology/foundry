package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.dto.FunctionParamDTO;

import java.util.List;

/**
 * Groovy 代码参数解析服务（仅供创建时反射解析 handle 签名建参数使用）。
 * <p>
 * 注意：脚本的编译与执行已统一下沉到 groovy-runner 容器，主应用不再执行用户代码，
 * 故本服务不再提供 executeGroovy；执行请走 {@link com.aircas.ptr.foundry.ontology.engine.ScriptEngineRouter}。
 */
public interface GroovyService {

    /**
     * 解析groovy代码片段，获取handle方法参数列表及返回值信息
     *
     * @param code
     * @return
     */
    List<FunctionParamDTO> parseGroovyCode(String code);

}
