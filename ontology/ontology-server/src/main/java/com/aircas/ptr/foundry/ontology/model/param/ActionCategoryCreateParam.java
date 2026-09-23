package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 行为分类体系创建请求。
 * <p>
 * 与 {@link OntologyCategoryCreateParam} 同构：一次性提交一棵子树，
 * parentId 为 0 表示新建体系（当前空间下尚不存在任何行为分类）。
 * <p>
 * 作用域为「本体空间」，继承 {@link OntologySpaceIdParam}（spaceId 必填，并校验空间存在性）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "行为分类体系创建")
public class ActionCategoryCreateParam extends OntologySpaceIdParam {

    @Schema(name = "parentId", description = "父节点id，为0表示当前节点为根节点(新创建行为体系)", example = "0")
    @NotNull(message = "parentId is null")
    private Integer parentId;

    @Schema(name = "name", description = "行为分类名称", example = "轨道机动")
    @NotBlank(message = "name is empty")
    private String name;

    @Schema(name = "children", description = "子节点")
    @Valid
    private List<CategoryNode> children;

}
