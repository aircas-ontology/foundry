package com.aircas.ptr.foundry.ontology.model.enums;

public enum ScriptRiskCategoryEnum {
    COMMAND_EXECUTION,
    DYNAMIC_EXECUTION,
    NETWORK_ACCESS,
    FILE_WRITE_DELETE,
    FILE_READ,
    PROCESS_THREAD,
    RESOURCE_EXHAUSTION,
    BYPASS,
    UNKNOWN;

    public static ScriptRiskCategoryEnum fromValue(String value) {
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
