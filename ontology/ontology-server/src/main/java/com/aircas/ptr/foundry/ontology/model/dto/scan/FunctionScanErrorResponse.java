package com.aircas.ptr.foundry.ontology.model.dto.scan;

import com.aircas.ptr.foundry.ontology.model.enums.ScanStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/** 扫描失败、解析失败及并发冲突的统一 data 结构。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "函数扫描错误数据")
public record FunctionScanErrorResponse(
        @Schema(description = "本次临时扫描状态") ScanStatus scanStatus,
        @Schema(description = "完整但未持久化的扫描结果") FunctionScanResult scanResult,
        @Schema(description = "稳定错误信息") ScanError error) {
}
