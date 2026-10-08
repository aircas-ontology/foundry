package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "发布函数版本参数")
public class FunctionVersionPublishParam {

    @NotBlank(message = "functionApi is empty")
    private String functionApi;

    @NotNull(message = "functionVersionId is null")
    @Schema(description = "待发布函数版本 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long functionVersionId;
}
