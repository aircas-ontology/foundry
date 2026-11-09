package com.aircas.ptr.foundry.ontology.service.behavior;

/**
 * 冷却节点的跨执行状态读写，由 OntologyActionService 用库表实现。
 * <p>节点由 behavior3java 反射实例化，拿不到 Spring 容器，冷却状态通过运行时回到服务层。</p>
 */
@FunctionalInterface
public interface TreeCooldownTracker {

    /**
     * 尝试占位：仍在冷却期返回 {@link CooldownDecision#inCooldown()}；否则记录本次执行时间并返回
     * {@link CooldownDecision#acquire()}。冷却状态表不可用时返回 {@link CooldownDecision#storeUnavailable()}，
     * 由节点决定放行并提示，避免辅助表缺失把整棵树执行打断。
     *
     * @param entityPrimaryKey 执行实例主键；为空时同一节点在整棵树范围共享冷却
     */
    CooldownDecision tryRun(Integer spaceId, String treeApi, String nodeId, Object entityPrimaryKey, int intervalSeconds);

    /**
     * 冷却占位结果：acquired 是否拿到本次执行许可；unavailable 表示冷却状态不可读写（如表未建）。
     */
    record CooldownDecision(boolean acquired, boolean unavailable) {

        public static CooldownDecision acquire() {
            return new CooldownDecision(true, false);
        }

        public static CooldownDecision inCooldown() {
            return new CooldownDecision(false, false);
        }

        public static CooldownDecision storeUnavailable() {
            return new CooldownDecision(true, true);
        }
    }
}
