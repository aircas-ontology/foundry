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
@Schema(description = "本体对象资源统计")
public class OntologyMetaStatisticVO {

    @Schema(name = "uniqueIdentifier", description = "本体id")
    private String uniqueIdentifier;

    @Schema(name = "entityCount", description = "实例数量")
    private Integer entityCount;

    @Schema(name = "propertyCount", description = "属性数量")
    private Integer propertyCount;

    @Schema(name = "relationCount", description = "关系数量")
    private Integer relationCount;

    @Schema(name = "actionCount", description = "行为数量")
    private Integer actionCount;
}
