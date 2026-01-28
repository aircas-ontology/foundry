package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.FunctionModelEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionTypeEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;

@NoArgsConstructor
@AllArgsConstructor
@Data
@SuperBuilder
@Accessors(chain = true)
@ApiModel(value = "函数基本信息VO")
public class FunctionInfoVO {

    @ApiModelProperty(name = "functionApi", value = "函数api", required = true)
    private String functionApi;

    @ApiModelProperty(name = "displayName", value = "函数名称", required = true)
    private String displayName;

    @ApiModelProperty(name = "description", value = "描述")
    private String description;

    @ApiModelProperty(name = "type", value = "函数模型")
    @NotNull(message = "model is null")
    private FunctionModelEnum model;

    @ApiModelProperty(name = "type", value = "函数类型")
    private FunctionTypeEnum type;
}
