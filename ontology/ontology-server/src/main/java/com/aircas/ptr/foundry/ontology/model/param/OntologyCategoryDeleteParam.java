package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;

@Data
@SuperBuilder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分类删除请求")
public class OntologyCategoryDeleteParam extends OntologySpaceIdParam {

    @Schema(name = "categoryId", description = "分类id", example = "1")
    @NotNull(message = "categoryId is null")
    private Integer categoryId;
}
