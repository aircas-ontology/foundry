package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @enumName: ActionHandleRuleAddConditionEnum
 * @author: yangj
 * @date: 2024/9/1 18:55
 * @version: 1.0
 * @description: 行为规则添加参数字段操作类型
 */
@Getter
@AllArgsConstructor
public enum ActionHandleRuleAddConditionEnum {

    LT("<", "小于"),
    LE("<=", "小于等于"),
    EQ("=", "等于"),
    NE("!=", "不等于"),
    GT(">", "大于"),
    GE(">=", "大于等于"),
    CH("!=", "变化");

    private final String code;
    private final String description;
}
