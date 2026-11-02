package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 行为的输出方式。
 * <p>
 * RESULT_ONLY 仅返回结果，执行完不落库；
 * WRITE_PROPERTY 写入对象属性，按出参绑定把算子输出回写到本体属性列。
 * 基础算子行为只能 RESULT_ONLY。
 */
@AllArgsConstructor
@Getter
public enum ActionOutputModeEnum {

    RESULT_ONLY(1, "仅返回结果"),
    WRITE_PROPERTY(2, "写入对象属性");

    private final int value;

    private final String name;

}
