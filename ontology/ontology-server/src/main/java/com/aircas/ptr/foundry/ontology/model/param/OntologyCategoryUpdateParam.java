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
@Schema(description = "分类更新请求")
public class OntologyCategoryUpdateParam extends OntologyCategoryDeleteParam {

    @Schema(name = "name", description = "属性分类名称", example = "平台")
    @NotBlank(message = "name is empty")
    private String name;
}
