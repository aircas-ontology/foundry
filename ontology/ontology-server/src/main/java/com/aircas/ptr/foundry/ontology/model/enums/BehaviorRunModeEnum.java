package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 容器节点（选择、顺序）的执行模式，落 JSON 存枚举名。
 * <p>
 * STANDARD 每个 Tick 从第一个子节点重新求值；MEMORY 记住上次成功的子节点，下次 Tick 从它开始；
 * REACTIVE 子节点返回 RUNNING 时保持原位，不重复触发。
 */
@AllArgsConstructor
@Getter
public enum BehaviorRunModeEnum {

    STANDARD(1, "标准"),
    MEMORY(2, "记忆"),
    REACTIVE(3, "响应式");

    private final int value;

    private final String name;

}
