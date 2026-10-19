package com.aircas.ptr.foundry.ontology.engine;

import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;

import java.util.Set;

/**
 * 脚本引擎抽象：屏蔽具体语言的执行方式，由 {@link ScriptEngineRouter} 按 {@link FunctionLanguageEnum} 选择实现。
 * <p>
 * 当前实现：
 * <ul>
 *   <li>{@link InProcessGroovyScriptEngine} - Groovy 进程内执行</li>
 *   <li>{@link InProcessJavaScriptEngine} - Java 进程内编译执行（可引用主程序类）</li>
 *   <li>{@link RemoteRunnerScriptEngine} - Python/TypeScript 远程 Runner 容器</li>
 * </ul>
 */
public interface ScriptEngine {

    /**
     * 该引擎支持的语言集合，用于 {@link ScriptEngineRouter} 建立语言到引擎的注册表
     */
    Set<FunctionLanguageEnum> supports();

    /**
     * 语法/编译校验（不执行）。失败抛业务异常。
     */
    void validate(ScriptExecutionContext ctx);

    /**
     * 统一执行：返回 output JSON（即 {@code FunctionResultVO} 的字段形态）。
     */
    String execute(ScriptExecutionContext ctx);

    /**
     * 主动失效该函数在引擎侧的编译/产物缓存。
     */
    void invalidate(FunctionLanguageEnum language, String functionApi);

}
