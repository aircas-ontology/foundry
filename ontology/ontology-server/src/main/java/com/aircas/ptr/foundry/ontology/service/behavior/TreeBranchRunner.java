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
     * <p>分支内部自己兜异常，返回值与 branches 等长；线程被中断时抛出，由节点记录失败。</p>
     */
    List<BehaviorStateEnum> run(List<Supplier<BehaviorStateEnum>> branches, int maxConcurrency) throws InterruptedException;
}
