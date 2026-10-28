package com.aircas.ptr.foundry.ontology.model.dto.scan;

import com.aircas.ptr.foundry.ontology.model.enums.ScanStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** 创建和修改成功时返回的扫描摘要。 */
@Schema(description = "函数保存扫描结果")
public record FunctionScanResponse(
        @Schema(description = "扫描状态", requiredMode = Schema.RequiredMode.REQUIRED)
        ScanStatus scanStatus,
        @Schema(description = "精简扫描结果", requiredMode = Schema.RequiredMode.REQUIRED)
        FunctionScanSummary scanResult) {

    public static FunctionScanResponse from(FunctionScanResult result) {
        return new FunctionScanResponse(result.status(), result.toSummary());
    }
}
