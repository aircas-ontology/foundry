package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.FunctionModelEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "函数更新请求")
public class FunctionUpdateParam {
    @NotBlank(message = "functionApi is empty")
    @Schema(description = "函数 API", requiredMode = Schema.RequiredMode.REQUIRED)
    private String functionApi;

    @NotNull(message = "functionVersionId is null")
    @Schema(description = "待更新的函数版本 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long functionVersionId;

    @NotBlank(message = "version is empty")
    @Pattern(regexp = "^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$",
            message = "version must match x.y.z")
    @Size(max = 64, message = "version length must not exceed 64")
    @Schema(description = "版本号，仅校验与 functionVersionId 一致，不允许修改",
            example = "1.2.31", requiredMode = Schema.RequiredMode.REQUIRED)
    private String version;

    @Schema(description = "描述")
    private String description;

    @NotBlank(message = "displayName is empty")
    @Schema(description = "函数名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String displayName;

    @Schema(description = "自定义函数代码")
    private String code;

    @Schema(description = "外部函数全限定名")
    private String referenceName;

    @NotNull(message = "model is null")
    @Schema(description = "函数模型", requiredMode = Schema.RequiredMode.REQUIRED)
    private FunctionModelEnum model;
}
