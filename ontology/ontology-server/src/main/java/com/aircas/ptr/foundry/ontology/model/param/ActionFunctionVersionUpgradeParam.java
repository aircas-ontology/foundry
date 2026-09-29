package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "行为函数版本升级请求；先预览，确认后再提交")
public class ActionFunctionVersionUpgradeParam {

    @NotBlank(message = "actionApi is empty")
    @Schema(description = "行为 API", requiredMode = Schema.RequiredMode.REQUIRED)
    private String actionApi;

    @NotNull(message = "targetFunctionVersionId is null")
    @Schema(description = "目标函数版本 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long targetFunctionVersionId;

    @Schema(description = "预览结果中的源函数版本 ID；确认升级时必填，用于防止并发覆盖")
    private Long sourceFunctionVersionId;

    @Schema(description = "false 仅预览差异；true 校验预览版本后事务落库")
    private Boolean confirm = false;
}
