package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 脚本函数（type=CUSTOMIZE / EXTERNAL）的脚本语言。
 * <p>
 * 所有语言统一通过独立的语言 Runner 容器执行，主应用不编译/执行任何用户代码；
 * 执行时按该枚举路由到对应 runner（{@code language → runner base URL} 严格一对一）。
 */
@AllArgsConstructor
@Getter
public enum FunctionLanguageEnum {

    GROOVY(1, "groovy"),
    JAVA(2, "java"),
    PYTHON(3, "python"),
    TYPESCRIPT(4, "typescript");

    private final int value;

    /**
     * 语言标识（小写），用于与前端字面量及配置 key 对齐
     */
    private final String code;

}
