package com.aircas.ptr.foundry.ontology.repository.param;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(description = "行为执行参数模型")
public class ActionHandleParam {

    @ApiModelProperty(value = "实体主键值", required = true, example = "7")
    private String primaryKey;

    @ApiModelProperty(value = "行为api", required = true, example = "shortDesc")
    private String api;

}
