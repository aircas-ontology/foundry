package com.aircas.ptr.foundry.ontology.repository.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "函数添加模型")
public class FunctionAddParam {

    @ApiModelProperty(value = "函数api", required = true, example = "getDesc")
    private String api;

    @ApiModelProperty(value = "函数描述", example = "这是一个函数")
    private String description;

    @ApiModelProperty(value = "函数涉及的本体，以\",\"分割", example = "xtmb")
    private String objectTypes;
}
