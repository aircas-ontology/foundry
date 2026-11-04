package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 并行节点的成败判定策略，落 JSON 存枚举名。
 * <p>
 * ALL_SUCCESS 全部子节点成功才算成功；ANY_SUCCESS 任一子节点成功即算成功；
 * ALL_FAILURE 全部子节点失败才算失败；ANY_FAILURE 任一子节点失败即算失败。
 */
@AllArgsConstructor
@Getter
public enum BehaviorParallelPolicyEnum {

    ALL_SUCCESS(1, "全部成功"),
    ANY_SUCCESS(2, "任一成功"),
    ALL_FAILURE(3, "全部失败"),
    ANY_FAILURE(4, "任一失败");

    private final int value;

    private final String name;

}
