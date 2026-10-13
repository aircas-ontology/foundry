package com.aircas.ptr.foundry.runner.lang;

import java.util.Map;

/**
 * JVM 语言脚本执行器抽象。groovy / java 各一个实现，由 {@code runner.language} 选择其一生效。
 */
public interface ScriptRunner {

    /**
     * 本执行器对应的语言标识：groovy / java
     */
    String language();

    /**
     * 仅编译校验（不执行）。校验失败抛 {@link com.aircas.ptr.foundry.runner.lang.ScriptCompileException}。
     */
    void validate(String code);

    /**
     * 编译并执行 handle(input)，返回 handle 的返回值（原样，由上层序列化为 JSON）。
     *
     * @param code  脚本源码
     * @param input 单对象入参（handle(input) 的 input）
     */
    Object execute(String code, Map<String, Object> input);

}
