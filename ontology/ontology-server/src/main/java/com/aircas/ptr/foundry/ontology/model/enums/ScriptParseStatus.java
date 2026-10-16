package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 脚本参数解析状态：区分「解析成功」与各类失败原因，避免把「语法错误 / runner 不可用 / 确实无可解析参数」
 * 混为一谈（前端据此给出差异化提示，而非一律回退手动声明）。
 */
@Getter
@AllArgsConstructor
public enum ScriptParseStatus {

    /** 成功定位 handle 并静态解析（params 仍可能为空，表示完全动态写法）。 */
    OK("解析成功"),
    /** 脚本语法错误，AST 无法构建。 */
    SYNTAX_ERROR("脚本语法错误"),
    /** 未找到 handle 入口方法。 */
    NO_HANDLE("缺少 handle 入口"),
    /** 该语言的 runner 未配置/未部署（仅 Python/TS）。 */
    RUNNER_NOT_CONFIGURED("语言 runner 未配置"),
    /** 调用 runner /parse 失败或 runner 返回错误（仅 Python/TS）。 */
    RUNNER_ERROR("runner 解析失败"),
    /** 解析过程抛出非预期异常。 */
    PARSE_EXCEPTION("解析异常"),

    ;

    private final String description;
}
