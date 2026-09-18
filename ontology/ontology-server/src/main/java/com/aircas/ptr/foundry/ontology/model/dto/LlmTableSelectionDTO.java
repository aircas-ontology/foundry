package com.aircas.ptr.foundry.ontology.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM 主表选择结果，用于反序列化模型返回的 JSON。
 * 严格匹配失败时（模型多输出解释文字等）由 Service 层做兜底解析。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmTableSelectionDTO {

    /**
     * LLM 选中的主表名
     */
    private String mainTable;

    /**
     * LLM 给出的选择理由
     */
    private String reasoning;
}
