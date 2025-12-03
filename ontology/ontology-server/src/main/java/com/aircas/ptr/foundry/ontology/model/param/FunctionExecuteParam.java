package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.controller.validator.FunctionApiVerify;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "函数执行参数")
public class FunctionExecuteParam {

    @ApiModelProperty(name = "functionApi", value = "函数api", required = true)
    @NotBlank(message = "functionApi is empty")
    @FunctionApiVerify
    private String functionApi;

    @ApiModelProperty(name = "parameters", value = "参数列表", required = true)
    private List<FunctionParameter> parameters;



}
