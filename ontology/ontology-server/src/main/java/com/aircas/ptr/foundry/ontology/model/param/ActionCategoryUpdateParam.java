package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;

/**
 * 行为分类更新请求（重命名，级联重写子树的 path）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "行为分类更新请求")
public class ActionCategoryUpdateParam extends ActionCategoryDeleteParam {

    @Schema(name = "name", description = "行为分类名称", example = "轨道机动")
    @NotBlank(message = "name is empty")
    private String name;
}
