package com.aircas.ptr.foundry.agent.tools.api.model;

/**
 * 本体分组工具视图对象。
 *
 * <p>Agent 工具契约模型，由 ontology-server 的分组查询原子操作映射产出。</p>
 *
 * @param groupId     分组 id
 * @param groupName   分组名称
 * @param description 分组描述
 * @param spaceId     所属本体空间 id
 */
public record OntologyGroupToolVO(
        String groupId,
        String groupName,
        String description,
        Integer spaceId
) {
}
