package com.aircas.ptr.foundry.ontology.model.dto.scan;

import com.aircas.ptr.foundry.ontology.model.enums.ScanStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** 发布接口响应；拒绝发布也是 HTTP 200，调用方必须检查 published。 */
@Schema(description = "函数发布结果")
public record FunctionPublishResponse(
        @Schema(description = "是否已发布", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean published,
        @Schema(description = "扫描状态", requiredMode = Schema.RequiredMode.REQUIRED)
        ScanStatus scanStatus,
        @Schema(description = "精简扫描结果", requiredMode = Schema.RequiredMode.REQUIRED)
        FunctionScanSummary scanResult) {

    public static FunctionPublishResponse from(FunctionPublishResult result) {
        return new FunctionPublishResponse(
                result.published(), result.scanStatus(), result.scanResult().toSummary());
    }
}
