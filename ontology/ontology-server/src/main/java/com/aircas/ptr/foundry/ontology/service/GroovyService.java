package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.dto.FunctionParamDTO;
import com.aircas.ptr.foundry.ontology.model.po.FunctionParamPO;

import java.util.List;
import java.util.Map;

/**
 * Groovy 代码参数解析 / 进程内执行服务。
 * <p>
 * 创建时用于反射解析 handle 签名建参数；脚本函数（CUSTOMIZE）的 Groovy 执行由
 * {@link com.aircas.ptr.foundry.ontology.engine.InProcessGroovyScriptEngine} 承载，本服务为其底座，
 * 函数编排（COMPOSITE）节点也通过本服务进程内执行原子函数 handle。
 */
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
     * 函数编排专用：执行原子函数 handle 方法并返回原始结果对象。
     *
     * <p>与 {@link #executeGroovy} 的区别是不做 JSON 序列化，且允许对象/列表类型的入参
     * （编排上游节点的产出就是 Map/List，无法转成基本类型）。</p>
     *
     * @param functionApi 函数唯一标识，用于缓存与失效
     * @param code        函数脚本
     * @param paramMap    入参名 -> 值
     * @param executeInputParams 函数入参定义，决定调用顺序
     * @return handle 方法返回值
     */
    Object invokeHandle(String functionApi, String code, Map<String, Object> paramMap,
                        List<FunctionParamPO> executeInputParams);

    /**
     * 函数更新/删除时主动失效编译缓存，避免旧 Class 长期占用 Metaspace。
     */
    void invalidateCompiledClass(String functionApi);
}
