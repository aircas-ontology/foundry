package com.aircas.ptr.foundry.ontology.model.dto.scan;

import io.swagger.v3.oas.annotations.media.Schema;

/** 列表及创建/修改/发布响应使用的精简扫描结果。 */
@Schema(description = "精简扫描结果，仅返回四项统计摘要")
public record FunctionScanSummary(
        @Schema(description = "扫描结果统计") ScanSummary summary) {
}
