package com.aircas.ptr.foundry.ontology.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 向导步骤 3 阶段一：大模型仅凭自身知识给出的"对象基本定义"响应。
 * <p>
 * 该定义在阶段二（结合数据源表清单与分类清单产出 {@link WizardObjectSpecDTO}）时作为
 * 领域认知基准，用于避免归类被分类名/表名望文生义地带偏。属于内部中间产物，不直接返回前端。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmObjectDefinitionDTO {

    /**
     * 对象所属领域/类型，如"陆战装备-机动火箭炮"；无法确定时为"未知"
     */
    private String domain;

    /**
     * 对象基本定义：本质、用途与关键特征
     */
    private String definition;

    /**
     * 常见别名 / 英文名 / 缩写
     */
    private List<String> aliases;
}
