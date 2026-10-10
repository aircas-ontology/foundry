package com.aircas.ptr.foundry.ontology.model.enums;

public enum ScriptRiskSeverityEnum {
    ERROR,
    WARNING,
    INFO,
    UNKNOWN;

    public static ScriptRiskSeverityEnum fromValue(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException exception) {
            return UNKNOWN;
        }
    }
}
