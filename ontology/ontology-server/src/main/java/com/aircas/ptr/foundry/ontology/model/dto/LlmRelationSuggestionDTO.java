package com.aircas.ptr.foundry.ontology.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM 单条关系建议的反序列化载体
 * <p>
 * LLM 返回结构示例：
 * <pre>
 * {
 *   "relations": [
 *     {"target":"aegis_system","relationType":"COMPOSITION","confidence":0.9,"reasoning":"..."}
 *   ]
 * }
 * </pre>
 * target 允许填 apiName 或 displayName，Service 层做归一化。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmRelationSuggestionDTO {

    /**
     * 目标本体的 apiName 或 displayName
     */
    private String target;

    /**
     * 关系类型枚举名，如 COMPOSITION / RECONNAISSANCE / STRIKE / COORDINATION / OTHER
     */
    private String relationType;

    /**
     * 置信度 0~1
     */
    private Double confidence;

    /**
     * 判断理由
     */
    private String reasoning;
}
