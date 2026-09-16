package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "本体属性元数据VO")
public class PropertyMetadataSchemaVO {

    @Schema(name = "schemaId", description = "schemaId", example = "1")
    private Integer schemaId;

    @Schema(name = "name", description = "名称", example = "等级")
    private String name;

    @Schema(name = "enumValues", description = "可用枚举值")
    private List<String> enumValues;

    @Schema(name = "children", description = "子节点")
    private List<PropertyMetadataSchemaVO> children;

}
