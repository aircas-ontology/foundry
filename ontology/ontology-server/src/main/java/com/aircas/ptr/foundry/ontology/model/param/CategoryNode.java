package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Accessors(chain = true)
@Schema(description = "分类节点")
public class CategoryNode {

    @Schema(hidden = true)
    private Integer parentId;

    @Schema(hidden = true)
    private String path;

    @Schema(name = "name", description = "分类名称", example = "航空母舰")
    @NotBlank(message = "name is empty")
    private String name;

    @Schema(name = "children", description = "子节点")
    private List<CategoryNode> children;

}
