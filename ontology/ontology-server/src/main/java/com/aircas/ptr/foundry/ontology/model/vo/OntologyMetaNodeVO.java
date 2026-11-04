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
@Schema(description = "对象节点信息")
public class OntologyMetaNodeVO {

    /**
     * 唯一标识
     */
    @Schema(name = "uniqueIdentifier")
    private String uniqueIdentifier;

    /**
     * 对象名称
     */
    @Schema(name = "displayName", description = "对象名称", example = "飞机")
    private String displayName;


    @Schema(name = "parentUniqueIdentifier")
    private String parentUniqueIdentifier;

    /**
     * 子对象
     */
    @Schema(name = "childNodes")
    private List<OntologyMetaNodeVO> childNodes;

}
