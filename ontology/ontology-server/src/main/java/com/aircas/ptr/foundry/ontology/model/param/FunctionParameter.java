package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "参数")
public class FunctionParameter {

    @ApiModelProperty(name = "paramName", value = "参数名称")
    private String paramName;

    @ApiModelProperty(name = "paramValue", value = "参数值")
    private Object paramValue;
}
