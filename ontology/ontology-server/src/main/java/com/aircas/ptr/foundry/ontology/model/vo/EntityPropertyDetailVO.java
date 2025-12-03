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
public class EntityPropertyDetailVO  {

    @ApiModelProperty(name = "tag", value = "属性标签", example = "基本属性")
    private String tag;

    @ApiModelProperty(name = "propertyUniqIdentifier", value = "属性名称", example = "名称")
    private String propertyUniqIdentifier;

    @ApiModelProperty(name = "propertyDisplayName", value = "属性名称", example = "名称")
    private String propertyDisplayName;

    @ApiModelProperty(name = "propertyValues", value = "属性值列表", example = "[123,456]")
    private List<Object> propertyValues;

}
