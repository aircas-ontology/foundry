package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 重复类节点的循环模式，落 JSON 存枚举名。
 * <p>
 * FIXED_TIMES 循环固定次数，次数取 times；INFINITE 无限循环，受节点超时时间约束；
 * UNTIL_CONDITION 按持续执行条件循环，条件空表示不启用。
 */
@AllArgsConstructor
@Getter
public enum BehaviorLoopModeEnum {

    FIXED_TIMES(1, "固定次数"),
    INFINITE(2, "无限循环"),
    UNTIL_CONDITION(3, "按条件");

    private final int value;

    private final String name;

}
