package com.aircas.ptr.foundry.ontology.model.dto.scan;

import com.aircas.ptr.foundry.ontology.model.enums.ScanStatus;

/** 发布编排结果；阶段四由控制器转换为正式接口响应。 */
public record FunctionPublishResult(
        boolean published,
        ScanStatus scanStatus,
        FunctionScanResult scanResult) {

    public static FunctionPublishResult of(boolean published, FunctionScanResult scanResult) {
        return new FunctionPublishResult(published, scanResult.status(), scanResult);
    }
}
