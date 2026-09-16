package com.aircas.ptr.foundry.ontology.model.vo;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
@Schema(description = "实体关系VO")
public class EntityLinksVO {

    @Schema(name = "ontologyUniqueIdentifier", description = "本体id", example = "abc")
    private String ontologyUniqueIdentifier;

    @Schema(name = "entityPrimaryKey", description = "实体主键值", example = "123")
    private Object entityPrimaryKey;

    @Schema(name = "links", description = "当前实体所有关联关系")
    private List<EntityLinkPropertyVO> links;
}
