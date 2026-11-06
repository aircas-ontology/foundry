package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 行为树节点类型，落 JSON 存枚举名。
 * <p>
 * 每种类型对应一份强类型 config（见 model.param.Behavior*Config），不在白名单内的类型或 config 字段一律拒绝，
 * 因为单轮引擎只会执行校验过的字段，配置写错在保存期就要拦下。
 */
@AllArgsConstructor
@Getter
public enum BehaviorNodeKindEnum {

    SELECTOR(1, "选择节点", BehaviorNodeCategoryEnum.COMPOSITE),
    SEQUENCE(2, "顺序节点", BehaviorNodeCategoryEnum.COMPOSITE),
    PARALLEL(3, "并行节点", BehaviorNodeCategoryEnum.COMPOSITE),

    INVERTER(4, "取反节点", BehaviorNodeCategoryEnum.DECORATOR),
    REPEATER(5, "重复节点", BehaviorNodeCategoryEnum.DECORATOR),
    UNTIL_SUCCESS(6, "直到成功", BehaviorNodeCategoryEnum.DECORATOR),
    UNTIL_FAILURE(7, "直到失败", BehaviorNodeCategoryEnum.DECORATOR),
    COOLDOWN(8, "冷却节点", BehaviorNodeCategoryEnum.DECORATOR),
    TIME_LIMIT(9, "限时节点", BehaviorNodeCategoryEnum.DECORATOR),
    FORCE_SUCCESS(10, "强制成功", BehaviorNodeCategoryEnum.DECORATOR),

    BLACKBOARD_CONDITION(11, "黑板条件", BehaviorNodeCategoryEnum.LEAF),
    CONDITION(12, "表达式条件", BehaviorNodeCategoryEnum.LEAF),
    ACTION(13, "行为节点", BehaviorNodeCategoryEnum.LEAF);

    private final int value;

    private final String name;

    /**
     * 子节点数量约束类别
     */
    private final BehaviorNodeCategoryEnum category;

}
