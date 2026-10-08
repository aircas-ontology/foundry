package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.controller.validator.FunctionApiVerify;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "函数执行参数")
public class FunctionExecuteParam {

    @Schema(name = "functionApi", description = "函数api", required = true)
    @NotBlank(message = "functionApi is empty")
    @FunctionApiVerify
    private String functionApi;

    @Schema(name = "parameters", description = "参数列表", required = true)
    private List<FunctionParameter> parameters;

    @NotNull(message = "functionVersionId is null")
    @Schema(description = "函数版本 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long functionVersionId;



}
