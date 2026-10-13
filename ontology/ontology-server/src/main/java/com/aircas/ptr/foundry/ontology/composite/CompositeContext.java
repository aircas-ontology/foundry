package com.aircas.ptr.foundry.ontology.composite;

import com.yomahub.liteflow.slot.DefaultContext;

/**
 * 函数编排执行上下文。LiteFlow 每次执行链时按类型创建实例，组件通过
 * {@code getContextBean(CompositeContext.class)} 读写运行期状态。
 */
public class CompositeContext extends DefaultContext {
}
