package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 脚本函数（type=CUSTOMIZE / EXTERNAL）的脚本语言。
 * <p>
 * Groovy/Java 在主程序 ontology-server 进程内编译执行（脚本可引用主程序内部类）；
 * Python/TypeScript 通过独立语言 Runner 容器执行，执行时按该枚举路由到对应 runner。
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
