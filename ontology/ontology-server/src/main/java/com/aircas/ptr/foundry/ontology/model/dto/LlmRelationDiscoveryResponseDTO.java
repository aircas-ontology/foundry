package com.aircas.ptr.foundry.ontology.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * LLM 关系发现响应的顶层包装
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmRelationDiscoveryResponseDTO {

    /**
     * 关系建议列表；LLM 判定无关系时应返回空数组
     */
    private List<LlmRelationSuggestionDTO> relations;
}
