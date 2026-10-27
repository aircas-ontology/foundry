package com.aircas.ptr.foundry.ontology.composite;

import com.aircas.ptr.foundry.ontology.model.bo.CompositeNodePlanBO;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 函数编排"转换"节点。
 * <p>脚本端口由 {@code CompositeTransformPortParser} 在编译期解析成画布端口，运行期把入参按端口名
 * 交给 {@link CompositeTransformExecutor} 执行脚本，再按输出端口名取回结果。</p>
 * <p>脚本返回单个标量（或只有一个输出端口）时，整个返回值直接给该端口，便于原型中常见的
 * {@code return {a, b}} 与 {@code return x} 两种写法都能工作。</p>
 */
public class CompositeTransformComponent extends CompositeDataComponent {

    private final CompositeTransformExecutor transformExecutor;

    public CompositeTransformComponent(CompositeTransformExecutor transformExecutor) {
        this.transformExecutor = transformExecutor;
    }

    @Override
    protected Map<String, Object> produce(CompositeRuntimeRequest request, CompositeNodePlanBO nodePlan,
                                          Map<String, Object> inputs) {
        Object scriptResult;
        // 根据映射模式选择执行方式
        if ("json".equals(nodePlan.getTransformMappingMode()) && nodePlan.getTransformMappings() != null) {
            scriptResult = transformExecutor.executeJsonMapping(nodePlan.getTransformMappings(), inputs);
        } else {
            scriptResult = transformExecutor.execute(nodePlan.getTransformLanguage(),
                    nodePlan.getTransformScript(), inputs);
        }
        Map<String, Object> outputs = new LinkedHashMap<>();
        Map<String, Object> fields = toFieldMap(scriptResult);
        boolean singlePort = nodePlan.getOutputParams().size() == 1;
        for (Map.Entry<String, String> port : nodePlan.getOutputParams().entrySet()) {
            String name = port.getValue();
            if (fields != null && fields.containsKey(name)) {
                outputs.put(name, fields.get(name));
            } else {
                outputs.put(name, singlePort ? scriptResult : null);
            }
        }
        return outputs;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toFieldMap(Object result) {
        if (result instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }
}
