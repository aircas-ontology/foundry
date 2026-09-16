package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "属性分类修改")
public class PropertyCategoryUpdateParam extends PropertyCategoryDeleteParam {


    @Schema(name = "name", description = "属性分类名称", example = "平台")
    @NotBlank(message = "name is empty")
    private String name;

}
