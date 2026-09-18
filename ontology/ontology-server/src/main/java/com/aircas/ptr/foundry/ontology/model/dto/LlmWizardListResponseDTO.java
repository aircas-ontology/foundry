package com.aircas.ptr.foundry.ontology.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 步骤 4 / 步骤 5 LLM 响应的顶层包装
 * <p>
 * 步骤 4 使用 properties 字段；步骤 5 使用 relations 字段。合并成一个类减少文件数，
 * Jackson 会自动忽略未出现的字段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmWizardListResponseDTO {

    /**
     * 步骤 4 LLM 返回的整体构建依据（提到外层，不内嵌到每个属性）
     */
    private String reasoning;

    /**
     * 步骤 4 LLM 返回的属性列表
     */
    private List<WizardPropertyDTO> properties;

    /**
     * 步骤 5 LLM 返回的关系列表
     */
    private List<LlmWizardRelationDTO> relations;
}
