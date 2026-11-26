package com.aircas.ptr.foundry.ontology.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 规则调度事件类型：新增 / 修改 / 删除。
 * 接口线上格式为中文「新增/修改/删除」，同时兼容旧写法 insert/update/delete（历史数据回显）。
 */
public enum RuleMonitorTypeEnum {

    INSERT("新增", "insert"),
    UPDATE("修改", "update"),
    DELETE("删除", "delete");

    private final String label;
    private final String legacy;

    RuleMonitorTypeEnum(String label, String legacy) {
        this.label = label;
        this.legacy = legacy;
    }

    @JsonCreator
    public static RuleMonitorTypeEnum fromWire(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (v.isEmpty()) {
            return null;
        }
        for (RuleMonitorTypeEnum e : values()) {
            if (e.label.equals(v) || e.legacy.equalsIgnoreCase(v) || e.name().equalsIgnoreCase(v)) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知事件类型：" + value + "，应为 新增/修改/删除");
    }

    @JsonValue
    public String toWire() {
        return label;
    }
}