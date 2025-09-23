package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@ApiModel(value = "实体属性值VO")
public class EntityPropertyVO extends OntologyPropertyInfoVO {

    @ApiModelProperty(name = "displayName", value = "属性名称", example = "名称")
    private String displayName;

    @ApiModelProperty(name = "propertyValue", value = "属性值", example = "123")
    private Object propertyValue;

}
