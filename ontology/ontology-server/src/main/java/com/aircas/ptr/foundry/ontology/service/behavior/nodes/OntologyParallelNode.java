package com.aircas.ptr.foundry.ontology.service.behavior.nodes;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.model.enums.BehaviorParallelPolicyEnum;
import com.aircas.ptr.foundry.ontology.model.enums.BehaviorStateEnum;
import com.aircas.ptr.foundry.ontology.model.param.BehaviorParallelConfig;
import com.aircas.ptr.foundry.ontology.service.behavior.BehaviorTreeRuntime;
import com.github.silencesu.behavior3java.config.BTNodeCfg;
import com.github.silencesu.behavior3java.constant.B3Status;
import com.github.silencesu.behavior3java.core.BaseNode;
import com.github.silencesu.behavior3java.core.Blackboard;
import com.github.silencesu.behavior3java.core.Composite;
import com.github.silencesu.behavior3java.core.Tick;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 并行节点：在单次执行内并发跑所有子节点，全部结束后按 policy 汇总。
 * <p>每个分支用独立 Tick 与独立 behavior3java Blackboard：Tick 的 openNodes/nodeCount 是可变状态，
 * 共享会互相污染；库内置记忆节点按节点 id 存状态，分支之间也不能共用一块黑板。
 * 业务变量仍走运行时同一张黑板 Map，分支写同一个键时以最后写入为准；分支里的行为必须能并发执行。
 * 所有分支都会跑完再判定，不因某个分支先失败而取消其他分支——行为已经落库，中途取消反而留下半套结果。
 * 被跳过的分支不参与成败判定：它没有表达「成功」或「失败」，把它算进策略会让 ALL_FAILURE 之类永远不成立。</p>
 */
@Slf4j
public class OntologyParallelNode extends Composite {

    private String nodeId;

    private BehaviorParallelConfig config;

    /** 定义里的子节点标识，顺序与分支一致，用来读分支的 skipped 标记 */
    private List<String> childNodeIds = List.of();

    @Override
    public void initialize(BTNodeCfg cfg) {
        super.initialize(cfg);
        var props = OntologyTreeNodes.props(cfg);
        this.nodeId = props.get("nodeId");
        this.config = OntologyTreeNodes.config(props, BehaviorParallelConfig.class);
        this.childNodeIds = OntologyTreeNodes.stringList(props, "childNodeIds");
    }

    @Override
    public B3Status onTick(Tick tick) {
        var runtime = OntologyTreeNodes.runtime(tick);
        var childCount = getChildCount();
        if (childCount == 0) {
            runtime.setNodeState(nodeId, BehaviorStateEnum.FAILURE);
            runtime.setNodeMessage(nodeId, "并行节点没有可执行的子节点");
            return B3Status.FAILURE;
        }
        var requested = config == null ? null : config.getMaxConcurrency();
        var desired = requested == null || requested <= 0 ? childCount : Math.min(requested, childCount);
        var concurrency = Math.min(desired, OntologyTreeNodes.MAX_PARALLEL_CONCURRENCY);
        List<BehaviorStateEnum> outcomes;
        try {
            var branches = new ArrayList<Supplier<BehaviorStateEnum>>(childCount);
            // 并行分支换线程执行，外层限时节点的截止时间要带过去，否则子树里的行为看不到限时
            var inheritedDeadlines = runtime.snapshotDeadlines();
            for (var i = 0; i < childCount; i++) {
                var child = getChild(i);
                var branch = branchTick(tick);
                branches.add(() -> {
                    var previous = runtime.snapshotDeadlines();
                    runtime.applyDeadlines(inheritedDeadlines);
                    try {
                        return runBranch(child, branch, runtime);
                    } finally {
                        runtime.applyDeadlines(previous);
                    }
                });
            }
            outcomes = runtime.runBranches(branches, concurrency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            runtime.setNodeState(nodeId, BehaviorStateEnum.FAILURE);
            runtime.setNodeMessage(nodeId, "并行执行被中断");
            return B3Status.FAILURE;
        }
        var counted = new ArrayList<BehaviorStateEnum>(outcomes.size());
        var skipped = 0;
        for (var i = 0; i < outcomes.size(); i++) {
            if (isBranchSkipped(runtime, i)) {
                skipped++;
            } else {
                counted.add(outcomes.get(i));
            }
        }
        if (counted.isEmpty()) {
            runtime.setNodeSkipped(nodeId, "全部 " + skipped + " 个分支都被跳过");
            return B3Status.SUCCESS;
        }
        var success = (int) counted.stream().filter(outcome -> outcome == BehaviorStateEnum.SUCCESS).count();
        var failure = counted.size() - success;
        var policy = config == null || config.getPolicy() == null
                ? BehaviorParallelPolicyEnum.ALL_SUCCESS : config.getPolicy();
        var hit = switch (policy) {
            case ALL_SUCCESS -> failure == 0;
            case ANY_SUCCESS -> success > 0;
            case ALL_FAILURE -> success == 0;
            case ANY_FAILURE -> failure > 0;
        };
        var state = hit ? BehaviorStateEnum.SUCCESS : BehaviorStateEnum.FAILURE;
        runtime.setNodeState(nodeId, state);
        if (concurrency < desired) {
            runtime.setNodeMessage(nodeId, "配置并发数 " + desired + " 超过单节点上限，按 "
                    + OntologyTreeNodes.MAX_PARALLEL_CONCURRENCY + " 执行");
        }
        if (skipped > 0) {
            runtime.appendNodeMessage(nodeId, skipped + " 个分支被跳过，不参与成败判定");
        }
        if (!hit) {
            runtime.appendNodeMessage(nodeId, "并行结果：成功 " + success + " / 失败 " + failure
                    + "，不满足策略" + policy.getName());
        }
        if (runtime.branchWaitTimedOut()) {
            runtime.appendNodeMessage(nodeId, "等待超过外层限时，未完成的分支按失败计入；"
                    + "这些分支里已经发起的行为可能仍在后台完成");
        }
        return OntologyTreeNodes.toB3(state);
    }

    private boolean isBranchSkipped(BehaviorTreeRuntime runtime, int index) {
        return index < childNodeIds.size() && runtime.isNodeSkipped(childNodeIds.get(index));
    }

    /** 每个分支一个 Tick；树与目标沿用父 Tick，behavior3java 的黑板独立 */
    private Tick branchTick(Tick parent) {
        var branch = new Tick();
        branch.setTarget(parent.getTarget());
        branch.setTree(parent.getTree());
        branch.setBlackboard(new Blackboard());
        return branch;
    }

    private BehaviorStateEnum runBranch(BaseNode child, Tick branchTick, BehaviorTreeRuntime runtime) {
        try {
            return OntologyTreeNodes.toState(child.execute(branchTick));
        } catch (Exception e) {
            // Guard 已经兜住节点异常，这里再兜一层，保证分支失败仍能参与策略汇总
            runtime.appendNodeMessage(nodeId, "分支执行异常：" + errorMessage(e));
            log.warn("行为树并行分支执行异常, nodeId={}", nodeId, e);
            return BehaviorStateEnum.FAILURE;
        }
    }

    private String errorMessage(Throwable e) {
        if (e == null) {
            return "未知异常";
        }
        return e instanceof BusinessException business ? business.getMessage() : e.getMessage();
    }
}
