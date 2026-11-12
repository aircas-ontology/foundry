package com.aircas.ptr.foundry.ontology.service.behavior;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorStateEnum;

import java.util.List;
import java.util.function.Supplier;

/**
 * 行为树节点能看到的运行时能力。
 * <p>节点由 behavior3java 反射实例化，拿不到 Spring 容器；所有需要访问库表（执行行为、回读属性）
 * 的动作都通过本接口回到运行时办理，节点自己只负责 BT 的返回值语义。
 * 黑板只对条件求值可见：接口不提供读写黑板的方法，行为参数取值与属性刷新都留在运行时内部。</p>
 */
public interface BehaviorTreeRuntime {

    Object getEntityPrimaryKey();

    Integer getSpaceId();

    /** 节点说明：失败原因、单轮降级提示等，尽量只写一次 */
    void setNodeMessage(String nodeId, String message);

    /** 追加节点说明，已有说明时用「；」连接 */
    void appendNodeMessage(String nodeId, String message);

    void setNodeState(String nodeId, BehaviorStateEnum state);

    /** 按跳过处理：状态记成功，并标记 skipped */
    void setNodeSkipped(String nodeId, String message);

    /** 进入节点时清掉上一轮留下的状态、跳过标记和说明，循环/重入时结果树才反映本次执行 */
    void resetNodeTrace(String nodeId);

    /** 节点本次执行是否被跳过；并行节点据此把跳过的分支排除在成败判定外 */
    boolean isNodeSkipped(String nodeId);

    /** 进入限时节点时压入自己的截止时间（毫秒时间戳），限时节点退出时必须弹出 */
    void pushDeadline(long deadlineMillis);

    /** 弹出最近一次压入的截止时间 */
    void popDeadline();

    /** 当前线程上是否有任一限时节点的截止时间已过 */
    boolean deadlineExceeded();

    /**
     * 领取一步执行预算：循环每轮、行为每次尝试都消耗一步。
     * <p>单节点的 1000 次上限仍然保留，但整棵树共享一份按节点数放大的预算，
     * 所以嵌套循环的次数是相加而不是相乘，不会放大到千万次落库。</p>
     *
     * @return 还有预算返回 true；用完返回 false，循环/重试应停止并按失败上报
     */
    boolean tryConsumeStep();

    /** 单次执行的总步数预算，用于节点说明 */
    int getStepBudget();

    /** 当前线程的截止时间快照，并行分支换线程执行时据此继承外层限时 */
    List<Long> snapshotDeadlines();

    /** 用快照替换当前线程的截止时间；传空集合表示清空，线程池线程复用前靠它恢复现场 */
    void applyDeadlines(List<Long> deadlines);

    /**
     * 求值 SpEL 表达式（跳过条件、持续执行条件、表达式条件共用）。
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

    /** 执行行为节点绑定的行为，真实落库；行为不会改动黑板 */
    BehaviorStateEnum executeAction(String nodeId, com.aircas.ptr.foundry.ontology.model.param.BehaviorActionConfig config);

    /**
     * 冷却占位：允许执行时记录本次执行时间；仍在冷却期返回 inCooldown；状态表不可用时返回 unavailable。
     * <p>状态跨执行持久，同一棵树、同一节点、同一执行实例共享一份记录。</p>
     *
     * @param entityPrimaryKeyKey 计时用的实例主键黑板键名；留空用执行请求里的 entityPrimaryKey
     */
    TreeCooldownTracker.CooldownDecision tryRunCooldown(String nodeId, int intervalSeconds, String entityPrimaryKeyKey);

    /**
     * 并发执行并行节点的分支，最多 maxConcurrency 个同时运行，按提交顺序返回结果。
     * <p>用应用统一线程池，节点自己不创建线程；分支内部自己兜异常。</p>
     */
    List<BehaviorStateEnum> runBranches(List<Supplier<BehaviorStateEnum>> branches, int maxConcurrency)
            throws InterruptedException;

    /**
     * 最近一次并行分支等待是否因限时提前结束。
     * <p>超时后未完成的分支按失败计，但它们的写操作可能仍在后台完成，并行节点据此补节点说明。</p>
     */
    default boolean branchWaitTimedOut() {
        return false;
    }
}
