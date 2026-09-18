package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "关系分类体系创建")
public class OntologyLinkCategoryCreateParam extends OntologySpaceIdParam {

    @Schema(name = "parentId", description = "父节点id，为0表示当前节点为根节点(新创建关系分类体系)", example = "0")
    @NotNull(message = "parentId is null")
    private Integer parentId;

    @Schema(name = "name", description = "分类名称", example = "亲属关系")
    @NotBlank(message = "name is empty")
    private String name;

    @Schema(name = "children", description = "子节点")
    @Valid
    private List<CategoryNode> children;

}
