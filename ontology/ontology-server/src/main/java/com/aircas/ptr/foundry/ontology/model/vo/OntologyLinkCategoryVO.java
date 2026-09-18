package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Schema(description = "关系分类VO")
public class OntologyLinkCategoryVO {

    @Schema(name = "categoryId", description = "分类id", example = "1")
    private Integer categoryId;

    @Schema(name = "name", description = "分类名称", example = "平台")
    private String name;

    @Schema(name = "links", description = "该分类下的关系")
    private List<OntologyLinkCategoryLinkVO> links;

    @Schema(name = "children", description = "子分类")
    private List<OntologyLinkCategoryVO> children;
}
