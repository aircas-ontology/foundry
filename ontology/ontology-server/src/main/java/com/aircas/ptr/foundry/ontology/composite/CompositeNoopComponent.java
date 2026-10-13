package com.aircas.ptr.foundry.ontology.composite;

import com.yomahub.liteflow.core.NodeComponent;

import java.util.LinkedHashMap;

/**
 * 函数编排空节点。
 * <p>编译器在 IF 分支目标为空或循环体为空时，用固定的全局节点 id {@code compositeNoop} 占位，
 * 保证 LiteFlow 的 EL 结构合法。该 id 不在执行计划的节点表里，因此只记录一次执行痕迹，
 * 不解析端口也不产出血统数据。</p>
 */
public class CompositeNoopComponent extends NodeComponent {

    @Override
    public void process() {
        CompositeRuntimeRequest request = CompositeDataComponent.requestOf(this);
        request.record(getNodeId(), new LinkedHashMap<>());
    }
}
