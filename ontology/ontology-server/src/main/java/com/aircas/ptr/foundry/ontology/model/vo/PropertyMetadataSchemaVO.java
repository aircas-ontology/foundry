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
@ApiModel(description = "本体属性元数据VO")
public class PropertyMetadataSchemaVO {

    @ApiModelProperty(name = "schemaId", value = "schemaId", example = "1")
    private Integer schemaId;

    @ApiModelProperty(name = "name", value = "名称", example = "等级")
    private String name;

    @ApiModelProperty(name = "enumValues", value = "可用枚举值")
    private List<String> enumValues;

    @ApiModelProperty(name = "children", value = "子节点")
    private List<PropertyMetadataSchemaVO> children;

}
