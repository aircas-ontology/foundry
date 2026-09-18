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
@Schema(description = "本体属性分类VO")
public class PropertyCategoryVO {

    @Schema(name = "categoryId", description = "分类id", example = "1")
    private Integer categoryId;

    @Schema(name = "name", description = "分类名称", example = "平台")
    private String name;

    @Schema(name = "children", description = "子分类")
    private List<PropertyCategoryVO> children;

}
