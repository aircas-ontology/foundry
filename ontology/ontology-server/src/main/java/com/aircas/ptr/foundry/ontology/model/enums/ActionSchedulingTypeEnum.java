package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @enumName: ActionSchedulingTypeEnum
 * @author: yangj
 * @date: 2024/9/1 19:07
 * @version: 1.0
 * @description: 行为执行类型
 */
@Getter
@AllArgsConstructor
public enum ActionSchedulingTypeEnum {

    TASK(1, "定时"),
    RULE(2, "规则");

    private final Integer code;
    private final String description;
}
