package com.aircas.ptr.foundry.ontology.model.request;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @className: ActionHandleMappingInParam
 * @author: yangj
 * @date: 2024/9/1 16:05
 * @version: 1.0
 * @description: 行为执行参数
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "行为执行参数")
public class ActionHandleMappingInParam {

    @ApiModelProperty(value = "参数值", required = true, example = "7")
    private Object parameterValue;

    @ApiModelProperty(value = "参数名称", required = true, example = "mbbh")
    private String parameterName;
}

