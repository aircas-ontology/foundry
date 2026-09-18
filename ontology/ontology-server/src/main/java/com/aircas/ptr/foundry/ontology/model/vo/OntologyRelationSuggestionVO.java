package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.OntologyLinkTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 单条本体关系建议
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Schema(description = "本体关系建议")
public class OntologyRelationSuggestionVO {

    @Schema(name = "targetUniqueIdentifier", description = "已有本体的唯一标识（对应 ontology_meta.unique_identifier）")
    private String targetUniqueIdentifier;

    @Schema(name = "targetDisplayName", description = "已有本体的显示名称", example = "宙斯盾系统")
    private String targetDisplayName;

    @Schema(name = "targetApiName", description = "已有本体的 apiName", example = "aegis_system")
    private String targetApiName;

    @Schema(name = "targetDescription", description = "已有本体的描述")
    private String targetDescription;

    @Schema(name = "relationType", description = "关系类型枚举，来自 OntologyLinkTypeEnum", example = "COMPOSITION")
    private OntologyLinkTypeEnum relationType;

    @Schema(name = "relationTypeName", description = "关系类型中文名，方便前端直接展示", example = "组合关系")
    private String relationTypeName;

    @Schema(name = "confidence", description = "置信度，取值 0~1，越大越可信", example = "0.9")
    private Double confidence;

    @Schema(name = "reasoning", description = "LLM 给出的判断理由")
    private String reasoning;
}
