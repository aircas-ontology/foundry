package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.FunctionModelEnum;
import com.aircas.ptr.foundry.ontology.controller.validator.FunctionApiVerify;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "函数更新请求")
public class FunctionUpdateParam {


    @Schema(name = "functionApi", description = "函数api", required = true)
    @NotBlank(message = "functionApi is empty")
    @FunctionApiVerify
    private String functionApi;

    @Schema(name = "description", description = "描述")
    private String description;

    @Schema(name = "displayName", description = "函数名称")
    @NotBlank(message = "displayName is empty")
    private String displayName;

    @Schema(name = "code", description = "自定义函数：函数代码")
    private String code;

    @Schema(name = "referenceName", description = "外部函数：函数全限定名")
    private String referenceName;

    @Schema(name = "type", description = "函数模型")
    @NotNull(message = "model is null")
    private FunctionModelEnum model;
}
