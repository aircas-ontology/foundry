package com.aircas.ptr.foundry.ontology.engine;

import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;
import com.aircas.ptr.foundry.ontology.model.po.FunctionParamPO;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 脚本执行上下文：封装一次脚本执行/校验所需的全部入参。
 * <p>
 * {@code inputJson} 为已按参数定义组装、序列化后的单对象入参（handle(input) 的 JSON），远程 runner 引擎只需要它。
 * {@code inputMap}/{@code inputParams} 供进程内引擎使用：Groovy 算子的 handle 是逐参数签名，
 * 没有参数表就无法把入参按位置展开调用。
 */
@Data
@Builder
public class ScriptExecutionContext {

    /**
     * 脚本语言，决定路由到哪个引擎
     */
    private FunctionLanguageEnum language;

    /**
     * 函数 api，用于引擎侧编译缓存键
     */
    private String functionApi;

    /**
     * 版本号
     */
    private String version;

    /**
     * 脚本源码
     */
    private String code;

    /**
     * 单对象入参的 JSON 字符串（handle(input) 的 input）
     */
    private String inputJson;

    /**
     * 已按参数定义转换过的入参名 -&gt; 值，进程内引擎按参数表顺序展开调用用
     */
    private Map<String, Object> inputMap;

    /**
     * 函数入参定义，决定进程内调用的参数顺序与类型
     */
    private List<FunctionParamPO> inputParams;

}
