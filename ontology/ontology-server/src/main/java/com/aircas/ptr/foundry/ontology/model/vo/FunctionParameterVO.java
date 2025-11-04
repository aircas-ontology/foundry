package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(value = "函数信息")
public class FunctionParameterVO {

    @ApiModelProperty(name = "paramName",value = "参数名称")
    private String parameterName;

    @ApiModelProperty(name = "paramType",value = "参数类型")
    private String parameterType;

    @ApiModelProperty(name = "description",value = "参数描述")
    private String description;
}
