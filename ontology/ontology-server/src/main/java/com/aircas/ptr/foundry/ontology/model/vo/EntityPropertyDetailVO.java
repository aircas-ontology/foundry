package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@ApiModel(value = "实体属性详情VO")
public class EntityPropertyDetailVO extends OntologyPropertyInfoVO {

    @ApiModelProperty(name = "propertyValues", value = "属性值列表（包含历史数据）", example = "[123,456]")
    private List<Object> propertyValues;

}
