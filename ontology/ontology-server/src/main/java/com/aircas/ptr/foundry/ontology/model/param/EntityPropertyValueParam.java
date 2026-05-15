package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ApiModel(description = "实体属性赋值参数")
public class EntityPropertyValueParam {

    @NotBlank(message = "propertyApiName is empty")
    @ApiModelProperty(name = "propertyApiName", value = "属性apiName", example = "name", required = true)
    private String propertyApiName;


    @NotNull(message = "propertyValue is null")
    @ApiModelProperty(name = "propertyValue", value = "属性值", example = "name", required = true)
    private Object propertyValue;


}
