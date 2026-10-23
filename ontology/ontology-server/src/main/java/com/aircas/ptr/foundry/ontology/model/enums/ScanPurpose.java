package com.aircas.ptr.foundry.ontology.model.enums;

/** 扫描调用目的，同时决定不含业务信息的 requestId 前缀。 */
public enum ScanPurpose {
    SAVE("function-save:"),
    PUBLISH("function-publish:");

    private final String requestIdPrefix;

    ScanPurpose(String requestIdPrefix) {
        this.requestIdPrefix = requestIdPrefix;
    }

    public String requestIdPrefix() {
        return requestIdPrefix;
    }
}
