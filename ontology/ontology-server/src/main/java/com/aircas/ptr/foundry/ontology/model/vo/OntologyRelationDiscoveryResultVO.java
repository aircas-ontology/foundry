package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 本体关系发现总体结果
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Schema(description = "本体关系发现结果")
public class OntologyRelationDiscoveryResultVO {

    @Schema(name = "spaceId", description = "本体空间id")
    private Integer spaceId;

    @Schema(name = "newOntologyName", description = "新本体名称，回显用", example = "阿利·伯克级驱逐舰")
    private String newOntologyName;

    @Schema(name = "existingOntologyCount", description = "同空间下已存在的本体数量（LLM 判定的候选池大小）")
    private Integer existingOntologyCount;

    @Schema(name = "relations", description = "关系建议列表；LLM 判断无关系时返回空数组")
    private List<OntologyRelationSuggestionVO> relations;
}
