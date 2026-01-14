package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
@ApiModel(value = "实体属性值VO")
public class EntityPropertyVO {



    @ApiModelProperty(name = "propertyApiName", value = "属性api名称", example = "名称")
    private String propertyApiName;


    @ApiModelProperty(name = "propertyName", value = "属性名称", example = "名称")
    private String propertyDisplayName;


    @ApiModelProperty(name = "propertyValue", value = "属性值", example = "123")
    private Object propertyValue;

}
