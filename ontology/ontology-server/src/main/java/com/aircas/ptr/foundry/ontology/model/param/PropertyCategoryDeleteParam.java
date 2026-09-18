package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "属性分类体系删除")
public class PropertyCategoryDeleteParam extends OntologyIdentifierParam {


    @Schema(name = "categoryId", description = "属性分类id", example = "1")
    @NotNull(message = "categoryId is null")
    private Integer categoryId;

}
