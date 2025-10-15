package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "函数执行参数")
public class FunctionExecuteParam {

    @ApiModelProperty(name = "functionApi", value = "函数api", required = true)
    @NotBlank(message = "functionApi is empty")
    private String functionApi;

    @ApiModelProperty(name = "parameters", value = "参数列表", required = true)
    private List<Parameter> parameters;



}
