package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;

@Data
@SuperBuilder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "关系分类更新请求")
public class OntologyLinkCategoryUpdateParam extends OntologyLinkCategoryDeleteParam {

    @Schema(name = "name", description = "关系分类名称", example = "平台")
    @NotBlank(message = "name is empty")
    private String name;

    @Schema(name = "color", description = "分类颜色（可选，不传则不修改）", example = "#FF0000")
    private String color;
}
