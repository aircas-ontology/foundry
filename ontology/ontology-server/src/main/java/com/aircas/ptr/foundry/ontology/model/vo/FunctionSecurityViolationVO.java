package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "函数代码安全违规项")
public class FunctionSecurityViolationVO {

    @Schema(description = "规则标识，如 call.Runtime#exec")
    private String ruleId;

    @Schema(description = "严重程度：DANGER / WARN")
    private String severity;

    @Schema(description = "威胁分类：COMMAND / FILE / NETWORK / REFLECTION / CLASSLOADER / JVM / ANNOTATION / INTERNAL / RESOURCE / SIZE / SCANNER")
    private String category;

    @Schema(description = "命中行号，-1 表示不适用")
    private int lineNumber;

    @Schema(description = "命中列号，-1 表示不适用")
    private int columnNumber;

    @Schema(description = "说明")
    private String message;

    @Schema(description = "命中的源码片段（已截断，最长 120 字符）")
    private String snippet;
}
