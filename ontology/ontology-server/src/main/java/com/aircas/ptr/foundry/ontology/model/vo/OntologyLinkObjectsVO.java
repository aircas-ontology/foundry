package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(description = "关系两侧的对象信息")
public class OntologyLinkObjectsVO {

    @Schema(name = "uniqueIdentifier", description = "关系id（ontology_link_group.unique_identifier）")
    private String uniqueIdentifier;

    @Schema(name = "name", description = "关系名称")
    private String name;

    @Schema(name = "fromObject", description = "开始对象（含对象id、对象名称、api名称、图标等）")
    private OntologyMetaInfoVO fromObject;

    @Schema(name = "toObject", description = "结束对象（含对象id、对象名称、api名称、图标等）")
    private OntologyMetaInfoVO toObject;
}
