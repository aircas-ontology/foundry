package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.controller.validator.FunctionApiVerify;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;

import static com.aircas.ptr.foundry.ontology.constant.FunctionConstant.VERSION_PATTERN;

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

    @Schema(name = "version", description = "版本号（必填，格式 x.y.z）", required = true)
    @NotBlank(message = "version is empty")
    @Pattern(regexp = VERSION_PATTERN, message = "版本号格式必须为 x.y.z（如 1.0.0）")
    private String version;

    @Schema(name = "parameters", description = "参数列表", required = true)
    private List<FunctionParameter> parameters;



}
