package com.aircas.ptr.foundry.ontology.service.behavior;

/**
 * 黑板上绑定了对象属性的键，在条件求值前按库里的当前值刷新。
 * <p>对象属性来源的键的值以库为准：出参只允许回传「写回属性就是本键绑定属性」的对象属性键，
 * 写回表后回读到的正是这份回传值，条件每次拿到的都是那一刻的属性真实值，不会被别的回传值顶掉。</p>
 */
@FunctionalInterface
public interface TreeBlackboardRefresher {

    /**
     * @param nodeId 触发求值的节点，定位不到实例之类的原因写进它的节点说明
     */
    void refresh(String nodeId, TreeExecutionRuntime runtime);
}
