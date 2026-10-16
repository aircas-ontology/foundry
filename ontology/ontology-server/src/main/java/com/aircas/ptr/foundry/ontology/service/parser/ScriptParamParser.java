package com.aircas.ptr.foundry.ontology.service.parser;

import com.aircas.ptr.foundry.ontology.model.dto.ScriptParseOutcome;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;

import java.util.Set;

/**
 * 脚本参数解析器：从脚本源码静态解析出 handle 入口的输入/输出参数。
 * <p>
 * Java/Groovy 在主应用进程内用 AST 解析（{@code GroovyScriptParamParser}/{@code JavaScriptParamParser}）；
 * Python/TypeScript 转发到对应语言 runner 容器的 {@code /parse} 端点（{@code RunnerScriptParamParser}）。
 */
public interface ScriptParamParser {

    /** 本解析器负责的语言集合，供路由注册。 */
    Set<FunctionLanguageEnum> supports();

    /**
     * 解析脚本参数。
     *
     * @param language 脚本语言
     * @param code     脚本源码
     * @return 解析产出（含状态与参数集合）；绝不返回 null，失败原因通过 {@code status}/{@code message} 透传
     */
    ScriptParseOutcome parse(FunctionLanguageEnum language, String code);
}
