package com.aircas.ptr.foundry.ontology.composite;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.model.bo.CompositeNodePlanBO;
import com.yomahub.liteflow.core.NodeComponent;

/**
 * 循环体每轮开始前的取数节点。
 * <p>循环节点本身只执行一次（它只负责给出次数或迭代器），LiteFlow 在每一轮开始前不会回调任何组件，
 * 所以"来自循环体外的上游取值"只在第一轮可见：之后该入参里留着的是上一轮的推送值。
 * 编译器把本节点作为循环体的第一个节点，每轮先执行它，把区域内各节点来自循环体外的入参重新铺回，
 * 区域外的上游值于是每轮都能重新生效。</p>
 * <p>循环体内还嵌着循环时，外层取数会替内层把后面几轮一并补上；内层组件随后自己执行时同一轮不重复记轨迹。</p>
 */
public class CompositeLoopPreloadComponent extends NodeComponent {

    /** 画布上不可见，仅用于日志与错误文案 */
    public static final String LABEL = "循环取数";

    @Override
    public void process() {
        CompositeRuntimeRequest request = CompositeDataComponent.requestOf(this);
        CompositeNodePlanBO nodePlan = CompositeDataComponent.planOf(this);
        // 外层那一轮已经替内层铺过本轮的值，这里只重放数据、不再记一次调用
        if (!request.preloadRound(nodePlan.getElId(), !isNested(request, nodePlan.getElId()))) {
            throw new BusinessException("编排循环取数节点[" + nodePlan.getElId() + "]未登记逐轮重放内容");
        }
    }

    /** 该取数节点是否位于另一个循环体内（其取数会被外层取数代为触发） */
    private boolean isNested(CompositeRuntimeRequest request, String preloadElId) {
        return request.isNestedPreload(preloadElId);
    }
}
