package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@ApiModel(description = "行为执行参数模型")
@AllArgsConstructor
@NoArgsConstructor
public class ActionHandleExecuteParam extends OntologyIdentifierParam {

    @ApiModelProperty(value = "实体主键值", required = false, example = "7")
    private String entityPrimaryKey;

    @ApiModelProperty(value = "行为id", required = true, example = "12345")
    private String actionId;

    @ApiModelProperty(value = "行为参数map", required = false, example = "[{\"parameterName\":\"mbbh\",\"parameterValue\":\"7\"}]")
    private List<ActionHandleMappingInParam> params;
}
