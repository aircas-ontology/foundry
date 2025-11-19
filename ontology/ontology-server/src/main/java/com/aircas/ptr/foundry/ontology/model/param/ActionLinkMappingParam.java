package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@ApiModel(description = "行为关系新增")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ActionLinkMappingParam {

    @ApiModelProperty(value = "行为关联的关系id")
    private String ontologyLinkUniqIdentifier;

    @ApiModelProperty(value = "行为关联的函数参数表达式")
    private String ontologyLinkFunctionParamExpression;

    @ApiModelProperty(value = "关系开始时间对应的函数参数ID", example = "1111")
    private Long startTimeFunctionParamId;

    @ApiModelProperty(value = "关系开始时间对应的函数参数的表达式", example = "data.user[0].name")
    private String startTimeFunctionParamExpression;

    @ApiModelProperty(value = "关系结束时间对应的函数参数ID", example = "1111")
    private Long endTimeFunctionParamId;

    @ApiModelProperty(value = "关系结束时间对应的函数参数的表达式", example = "data.user[0].name")
    private String endTimeFunctionParamExpression;
}
