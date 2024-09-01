package com.aircas.ptr.foundry.ontology.repository.param;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author yangj
 */
@Data
@ApiModel(description = "行为执行参数模型")
@AllArgsConstructor
@NoArgsConstructor
public class ActionHandleParam {

    @ApiModelProperty(value = "实体主键值", required = false, example = "7")
    private String primaryKey;

    @ApiModelProperty(value = "行为api", required = true, example = "shortDesc")
    private String api;

    @ApiModelProperty(value = "行为参数map", required = false, example = "[{\"parameterName\":\"mbbh\",\"parameterValue\":\"7\"}]")
    private List<ActionHandleMappingInParam> params;
}
