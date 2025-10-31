package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.common.constant.FunctionTypeEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "函数请求")
public class FunctionUpdateParam {



    @ApiModelProperty(name = "functionApi", value = "函数api", required = true)
    @NotBlank(message = "functionApi is empty")
    private String functionApi;

    @ApiModelProperty(name = "description", value = "描述")
    private String description;

    @ApiModelProperty(name = "type", value = "函数类型")
    @NotNull(message = "type is null")
    private FunctionTypeEnum type;

    @ApiModelProperty(name = "code", value = "自定义函数code")
    private String code;

    @ApiModelProperty(name = "referenceName", value = "已存在函数的全限定名")
    private String referenceName;
}
