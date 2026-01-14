package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @enumName: ActionRuleConnectType
 * @author: yangj
 * @date: 2024/9/1 18:49
 * @version: 1.0
 * @description: 行为执行规则拼接类型
 */
@Getter
@AllArgsConstructor
public enum ActionRuleConnectType {

    AND(1, "且"),
    OR(2, "或");

    private final Integer code;
    private final String description;
}
