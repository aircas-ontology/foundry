package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Schema(description = "关系分类下挂载的关系项")
public class OntologyLinkCategoryLinkVO {

    @Schema(name = "id", description = "关系id", example = "1")
    private Long id;

    @Schema(name = "name", description = "关系名称", example = "父子")
    private String name;

    @Schema(name = "type", description = "关系类型", example = "HIERARCHY")
    private String type;
}
