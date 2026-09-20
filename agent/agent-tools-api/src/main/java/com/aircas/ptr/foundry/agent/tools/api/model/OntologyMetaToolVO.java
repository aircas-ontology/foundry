package com.aircas.ptr.foundry.agent.tools.api.model;

/**
 * 本体元数据工具视图对象。
 *
 * <p>Agent 工具契约模型，由 ontology-server 的原子操作映射产出，供大模型工具调用消费。
 * 仅保留大模型理解与回答所需的核心字段，避免把完整 PO/VO 细节泄露给模型上下文。</p>
 *
 * @param uniqueIdentifier 本体唯一标识
 * @param displayName      本体名称
 * @param description      本体描述
 * @param apiName          代码中使用的本体名称
 * @param spaceId          所属本体空间 id
 * @param entityCount      实例数量
 * @param relationCount    关系数量
 * @param propertyCount    属性数量
 */
public record OntologyMetaToolVO(
        String uniqueIdentifier,
        String displayName,
        String description,
        String apiName,
        Integer spaceId,
        Integer entityCount,
        Integer relationCount,
        Integer propertyCount
) {
}
