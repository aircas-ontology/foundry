package com.aircas.ptr.foundry.ontology.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 规则判断操作数类型：dynamic 取对象实例的属性当前值，fixed 用固定值。
 * 接口线上格式为小写 dynamic/fixed。
 */
public enum RuleOperateType {

    DYNAMIC,
    FIXED;

    @JsonCreator
    public static RuleOperateType fromWire(String value) {
        if (value == null) {
            return null;
        }
        return valueOf(value.trim().toUpperCase());
    }

    @JsonValue
    public String toWire() {
        return name().toLowerCase();
    }
}