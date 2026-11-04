package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 变量作用域，落 JSON 存枚举名。
 * <p>
 * 目前只有 RUNTIME：值只在一次树执行内存在，执行结束即丢弃，所以不建运行时状态表。
 * 这也意味着依赖跨执行状态的冷却时间、重试次数无法真正生效，引擎侧要显式报不支持。
 */
@AllArgsConstructor
@Getter
public enum BehaviorVariableScopeEnum {

    RUNTIME(1, "运行时");

    private final int value;

    private final String name;

}
