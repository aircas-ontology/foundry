package com.aircas.ptr.foundry.ontology.service.behavior;

/**
 * 黑板上绑定了对象属性的键，在条件求值前按库里的当前值刷新。
 * <p>行为不会再写黑板，属性值只能通过这条路进来：谁写了属性都不影响条件读到的结果，
 * 条件每次都拿到那一刻的真实值。</p>
 */
@FunctionalInterface
public interface TreeBlackboardRefresher {

    /**
     * @param nodeId 触发求值的节点，定位不到实例之类的原因写进它的节点说明
     */
    void refresh(String nodeId, TreeExecutionRuntime runtime);
}
