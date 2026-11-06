package com.aircas.ptr.foundry.ontology.service.behavior;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorStateEnum;

/**
 * 行为树节点能看到的运行时能力。
 * <p>节点由 behavior3java 反射实例化，拿不到 Spring 容器；所有需要访问库表（执行行为、回读属性）
 * 的动作都通过本接口回到运行时办理，节点自己只负责 BT 的返回值语义。</p>
 */
public interface BehaviorTreeRuntime {

    Object getEntityPrimaryKey();

    Integer getSpaceId();

    Object blackboardValue(String key);

    void setBlackboardValue(String key, Object value);

    /** 节点说明：失败原因、单轮降级提示等，尽量只写一次 */
    void setNodeMessage(String nodeId, String message);

    /** 追加节点说明，已有说明时用「；」连接 */
    void appendNodeMessage(String nodeId, String message);

    void setNodeState(String nodeId, BehaviorStateEnum state);

    /** 按跳过处理：状态记成功，并标记 skipped */
    void setNodeSkipped(String nodeId, String message);

    /**
     * 求值 SpEL 表达式（跳过条件、表达式条件共用）。
     *
     * @return 求值结果；表达式非法或求值异常时返回 null，原因写入节点说明
     */
    Boolean evaluateExpression(String nodeId, String expression);

    /**
     * 按配置比较黑板值。
     *
     * @return 比较结果；类型对不上返回 null，原因写入节点说明
     */
    Boolean evaluateBlackboardCondition(String nodeId, com.aircas.ptr.foundry.ontology.model.param.BehaviorBlackboardConditionConfig config);

    /** 执行行为节点绑定的行为，真实落库并回填黑板 */
    BehaviorStateEnum executeAction(String nodeId, com.aircas.ptr.foundry.ontology.model.param.BehaviorActionConfig config);
}
