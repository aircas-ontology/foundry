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
@ApiModel(description = "函数更新请求")
public class FunctionUpdateParam {


    @ApiModelProperty(name = "functionApi", value = "函数api", required = true)
    @NotBlank(message = "functionApi is empty")
    private String functionApi;

    @ApiModelProperty(name = "description", value = "描述")
    private String description;

    @ApiModelProperty(name = "displayName", value = "函数名称")
    @NotBlank(message = "displayName is empty")
    private String displayName;

    @ApiModelProperty(name = "code", value = "自定义函数：函数代码")
    private String code;

    @ApiModelProperty(name = "referenceName", value = "外部函数：函数全限定名")
    private String referenceName;
}
