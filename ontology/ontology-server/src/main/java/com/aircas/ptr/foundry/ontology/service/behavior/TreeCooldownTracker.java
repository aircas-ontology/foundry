package com.aircas.ptr.foundry.ontology.service.behavior;

/**
 * 冷却节点的跨执行状态读写，由 OntologyActionService 用库表实现。
 * <p>节点由 behavior3java 反射实例化，拿不到 Spring 容器，冷却状态通过运行时回到服务层。</p>
 */
@FunctionalInterface
public interface TreeCooldownTracker {

    /**
     * 尝试占位：仍在冷却期返回 false；否则记录本次执行时间并返回 true。
     *
     * @param entityPrimaryKey 执行实例主键；为空时同一节点在整棵树范围共享冷却
     */
    boolean tryRun(Integer spaceId, String treeApi, String nodeId, Object entityPrimaryKey, int intervalSeconds);
}
