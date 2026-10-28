package com.aircas.ptr.foundry.ontology.composite;

import com.aircas.ptr.foundry.ontology.model.bo.CompositeNodePlanBO;
import com.yomahub.liteflow.core.NodeSwitchComponent;

import java.util.Map;

/**
 * 函数编排"Switch Case"节点。
 * <p>读取 {@code switchOnPath} 指定的入参值，按顺序与 {@code switchCases} 中的 value 比较，
 * 命中时返回对应 case.id 对应的 LiteFlow 跳转串；未命中时走 {@code default}。</p>
 */
public class CompositeSwitchComponent extends NodeSwitchComponent {

    public static final String DEFAULT_TARGET = "default";

    /**
     * LiteFlow 的 tag 跳转判定要求返回值为 {@code tag:<分支 tag>}：
     * {@code SwitchCondition} 仅在返回值含 {@code :} 时才按 tag 匹配目标区域。
     */
    private static final String TAG_PREFIX = "tag:";

    @Override
    public String processSwitch() throws Exception {
        CompositeRuntimeRequest request = CompositeDataComponent.requestOf(this);
        CompositeNodePlanBO nodePlan = CompositeDataComponent.planOf(this);
        Map<String, Object> inputs = CompositeDataComponent.resolveInputs(this, request, nodePlan);
        request.record(nodePlan.getElId(), inputs);
        String switchOnPath = nodePlan.getSwitchOnPath();
        Object value = isBlank(switchOnPath) ? inputs.get("value") : inputs.get(switchOnPath);
        if (value == null) {
            return TAG_PREFIX + DEFAULT_TARGET;
        }
        String text = value.toString();
        for (Map.Entry<String, String> entry : nodePlan.getSwitchCases().entrySet()) {
            if (text.equals(entry.getValue())) {
                return TAG_PREFIX + entry.getKey();
            }
        }
        return TAG_PREFIX + DEFAULT_TARGET;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
