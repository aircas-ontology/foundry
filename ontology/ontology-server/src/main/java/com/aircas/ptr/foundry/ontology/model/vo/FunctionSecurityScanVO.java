package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "函数代码安全检测结果")
public class FunctionSecurityScanVO {

    @Schema(description = "是否通过；false 表示保存会被拒绝")
    private boolean passed;

    @Schema(description = "最高严重程度：DANGER / WARN，无违规时为 null")
    private String highestSeverity;

    @Schema(description = "一行摘要，可直接展示给用户")
    private String summary;

    @Schema(description = "扫描耗时（毫秒）")
    private long scanDurationMs;

    @Schema(description = "违规明细")
    private List<FunctionSecurityViolationVO> violations;
}
