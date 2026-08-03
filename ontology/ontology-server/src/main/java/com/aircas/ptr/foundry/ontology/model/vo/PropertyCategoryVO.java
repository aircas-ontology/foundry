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
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@ApiModel(description = "本体属性可见性VO")
public class PropertyCategoryVO {

    @ApiModelProperty(name = "categoryId", value = "分类id", example = "1")
    private Integer categoryId;

    @ApiModelProperty(name = "name", value = "分类名称", example = "平台")
    private String name;

    @ApiModelProperty(name = "children", value = "子分类")
    private List<PropertyCategoryVO> children;

}
