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

    @Schema(name = "uniqueIdentifier", description = "关系唯一标识", example = "link_001")
    private String uniqueIdentifier;

    @Schema(name = "name", description = "关系名称", example = "父子")
    private String name;

    @Schema(name = "type", description = "关系类型", example = "HIERARCHY")
    private String type;

    @Schema(name = "categoryId", description = "关系所属分类id（ontology_link_category.id）")
    private Integer categoryId;

    @Schema(name = "ontologyUniqueIdentifierFrom", description = "开始本体unique identifier")
    private String ontologyUniqueIdentifierFrom;

    @Schema(name = "ontologyNameFrom", description = "开始本体名称")
    private String ontologyNameFrom;

    @Schema(name = "ontologyUniqueIdentifierTo", description = "结束本体unique identifier")
    private String ontologyUniqueIdentifierTo;

    @Schema(name = "ontologyNameTo", description = "结束本体名称")
    private String ontologyNameTo;

    @Schema(name = "ontologyIconFrom", description = "开始本体icon")
    private String ontologyIconFrom;

    @Schema(name = "ontologyIconTO", description = "结束本体icon")
    private String ontologyIconTO;

    @Schema(name = "apiName", description = "apiName")
    private String apiName;

    @Schema(name = "description", description = "description")
    private String description;


}
