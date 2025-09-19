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

    @ApiModelProperty(name = "functionName",value = "函数名称",required = true)
    @NotBlank(message = "functionName is empty" )
    private String functionName;

    @ApiModelProperty(name = "objectTypes",value = "本体列表", required = true)
    @NotEmpty(message = "objectTypes is empty" )
    private List<String> objectTypes;

    @ApiModelProperty(name = "parameters",value = "参数", required = true)
    @NotNull(message = "parameters is empty")
    private HashMap<String, Object> parameters;
}
