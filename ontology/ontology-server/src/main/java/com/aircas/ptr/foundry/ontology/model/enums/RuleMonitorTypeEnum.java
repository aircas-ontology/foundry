package com.aircas.ptr.foundry.ontology.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 规则调度监测类型：监测数据湖表的行级事件（新增/更新/删除）。
 * 接口线上格式为小写 insert/update/delete。
 */
public enum RuleMonitorTypeEnum {

    INSERT,
    UPDATE,
    DELETE;

    @JsonCreator
    public static RuleMonitorTypeEnum fromWire(String value) {
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