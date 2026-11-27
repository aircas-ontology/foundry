package com.aircas.ptr.foundry.ontology.service.behavior;

/**
 * 冷却节点在子节点本轮被跳过时退还刚占的冷却，由 OntologyActionService 用库表实现。
 * <p>节点由 behavior3java 反射实例化，拿不到 Spring 容器，冷却状态通过运行时回到服务层。
 * 与 {@link TreeCooldownTracker} 分开：占位要原子判断间隔，退还是按同一 scopeKey 删记录，语义不同。</p>
 */
@FunctionalInterface
public interface TreeCooldownRefunder {

    /**
     * 退还本次占位：删除与该节点本次执行完全相同 scopeKey 的冷却记录，让下一轮能重新计时。
     *
     * @param entityPrimaryKey 占位时用的实例主键，按与占位同样的规则算出 scopeKey，确保删到同一条
     */
    void refund(Integer spaceId, String treeApi, String nodeId, Object entityPrimaryKey);
}
