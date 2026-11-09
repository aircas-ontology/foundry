package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 行为树节点的执行结果状态，落 JSON 存枚举名。
 * <p>
 * 引擎一次 Tick 返回 SUCCESS 或 FAILURE；RUNNING 表示节点仍在执行、下次 Tick 续跑，
 * 单轮引擎不产生该状态。
 */
@AllArgsConstructor
@Getter
public enum BehaviorStateEnum {

    SUCCESS(1, "成功"),
    FAILURE(2, "失败"),
    RUNNING(3, "执行中");

    private final int value;

    private final String name;

}
