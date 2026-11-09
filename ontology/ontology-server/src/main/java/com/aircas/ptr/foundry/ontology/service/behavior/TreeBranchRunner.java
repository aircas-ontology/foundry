package com.aircas.ptr.foundry.ontology.service.behavior;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorStateEnum;

import java.util.List;
import java.util.function.Supplier;

/**
 * 并行节点的分支执行能力，由 OntologyActionService 用应用线程池实现。
 * <p>节点由 behavior3java 反射实例化，拿不到 Spring 容器，也不能自己创建线程；
 * 分支统一提交到 ThreadPoolConfig 里的线程池，按节点配置的并发数限流。</p>
 */
@FunctionalInterface
public interface TreeBranchRunner {

    /**
     * 并发执行全部分支，最多 maxConcurrency 个同时运行，按提交顺序返回结果。
     * <p>分支内部自己兜异常，返回值与 branches 等长；线程被中断时抛出，由节点记录失败。
     * 外层限时还在时传截止时间（毫秒时间戳），等待不超过该时刻，没跑完的分支按失败返回；
     * 传小于等于 0 表示没有限时。</p>
     */
    BranchRun run(List<Supplier<BehaviorStateEnum>> branches, int maxConcurrency, long deadlineMillis)
            throws InterruptedException;

    /**
     * @param states   按提交顺序的分支结果；未完成的分支为 FAILURE
     * @param timedOut 是否因限时提前结束，未完成的分支可能仍在后台执行
     */
    record BranchRun(List<BehaviorStateEnum> states, boolean timedOut) {
    }
}
