package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.FunctionModelEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "函数请求")
public class FunctionCreateParam {

    @Schema(name = "functionApi", description = "函数api")
    @NotBlank(message = "functionApi is empty")
    private String functionApi;

    @Schema(name = "displayName", description = "函数名称")
    @NotBlank(message = "displayName is empty")
    private String displayName;

    @Schema(name = "description", description = "描述")
    private String description;

    @Schema(name = "type", description = "函数类型")
    @NotNull(message = "type is null")
    private FunctionTypeEnum type;

    @Schema(name = "type", description = "函数模型")
    @NotNull(message = "model is null")
    private FunctionModelEnum model;

    @Schema(name = "code", description = "自定义函数：函数代码")
    private String code;

    @Schema(name = "referenceName", description = "外部函数：函数全限定名")
    private String referenceName;

    @Schema(description = "版本变更说明")
    private String changeLog;

    @Pattern(regexp = "^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$",
            message = "version must match x.y.z")
    @Size(max = 64, message = "version length must not exceed 64")
    @Schema(description = "自定义版本号，严格为 x.y.z；为空时默认 1.0.0", example = "1.0.0")
    private String version;

    @Schema(description = "创建后立即发布")
    private Boolean publish;
}
