package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "函数版本操作参数")
public class FunctionVersionParam {
    @NotBlank(message = "functionApi is empty")
    private String functionApi;
    @NotNull(message = "functionVersionId is null")
    @Schema(description = "函数版本 ID。创建新版本时表示源版本，发布时表示待发布版本")
    private Long functionVersionId;
    private String code;
    private String referenceName;
    private String changeLog;
}
