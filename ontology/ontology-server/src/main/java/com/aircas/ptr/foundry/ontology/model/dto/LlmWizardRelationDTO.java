package com.aircas.ptr.foundry.ontology.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 步骤 5 LLM 返回的单条关系（未 enrich 前的原始结构）
 * <p>
 * 后端会根据 targetApiName 在候选清单里查找，补齐 targetUniqueIdentifier / targetObjectName /
 * targetObjectDescription 等字段，再拼装成 WizardRelationVO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmWizardRelationDTO {

    /**
     * 关系名称，如"装备宙斯盾系统"
     */
    private String name;

    /**
     * 目标本体的 apiName（必须在候选清单中出现）
     */
    private String targetApiName;

    /**
     * 关系类型枚举名，如 COMPOSITION / RECONNAISSANCE / STRIKE / COORDINATION / OTHER
     */
    private String type;

    /**
     * 关系说明
     */
    private String description;
}
