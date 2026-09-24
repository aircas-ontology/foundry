package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;

/**
 * 行为分类删除请求。作用域为「本体空间」。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "行为分类删除请求")
public class ActionCategoryDeleteParam extends OntologySpaceIdParam {

    @Schema(name = "categoryId", description = "行为分类id", example = "1")
    @NotNull(message = "categoryId is null")
    private Integer categoryId;
}
