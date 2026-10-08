package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "创建函数版本参数")
public class FunctionVersionCreateParam {

    @NotBlank(message = "functionApi is empty")
    private String functionApi;

    @NotNull(message = "sourceFunctionVersionId is null")
    @Schema(description = "源已发布函数版本 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sourceFunctionVersionId;

    @NotBlank(message = "version is empty")
    @Pattern(regexp = "^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$",
            message = "version must match x.y.z")
    @Size(max = 64, message = "version length must not exceed 64")
    @Schema(description = "新版本的自定义版本号", example = "1.2.31",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String version;

    private String code;
    private String referenceName;
    private String changeLog;
}
