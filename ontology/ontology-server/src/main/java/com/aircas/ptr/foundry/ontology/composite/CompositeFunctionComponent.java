package com.aircas.ptr.foundry.ontology.composite;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.model.bo.CompositeNodePlanBO;
import com.aircas.ptr.foundry.ontology.model.po.Function;
import com.aircas.ptr.foundry.ontology.model.po.FunctionParamPO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionResultVO;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 函数编排"算子"节点。
 * <p>按被引用算子的类型分发执行（脚本函数算子走 Groovy {@code handle} 方法、基础函数算子走本体属性
 * 通用查询、外部API函数算子按接口定义发起 HTTP 调用），再把返回值映射回画布上的输出端口；
 * 分发逻辑见 {@link CompositeFunctionInvoker}。</p>
 * <p>LiteFlow 的节点实例在注册时与节点 id 一一绑定，因此函数定义、参数定义等协作者通过构造器注入，
 * 不依赖 Spring 上下文持有器。</p>
 */
public class CompositeFunctionComponent extends CompositeDataComponent {

    private final CompositeFunctionCatalog catalog;

    private final CompositeFunctionInvoker invoker;

    private final ObjectMapper objectMapper;

    public CompositeFunctionComponent(CompositeFunctionCatalog catalog, CompositeFunctionInvoker invoker,
                                      ObjectMapper objectMapper) {
        this.catalog = catalog;
        this.invoker = invoker;
        this.objectMapper = objectMapper;
    }

    @Override
    protected Map<String, Object> produce(CompositeRuntimeRequest request, CompositeNodePlanBO nodePlan,
                                          Map<String, Object> inputs) {
        String functionApi = nodePlan.getFunctionApi();
        Function function = catalog.findFunction(functionApi);
        if (function == null) {
            throw new BusinessException("编排节点[" + nodePlan.getLabel() + "]引用的函数不存在：" + functionApi);
        }
        List<FunctionParamPO> inputParams = catalog.findInputParams(function.getId());
        Object result = invoker.invoke(function, nodePlan, inputs, inputParams);
        return bindOutputs(nodePlan, unwrap(result));
    }

    /** 原子函数约定返回 FunctionResultVO，编排关心的是其中的业务数据 */
    private Object unwrap(Object result) {
        if (result instanceof FunctionResultVO<?> resultVO) {
            Object data = resultVO.getData();
            return data != null ? data : resultVO;
        }
        return result;
    }

    /**
     * 把函数返回值映射到画布输出端口：能拆成键值对的结果按端口名取值，
     * 只有一个输出端口时直接给整个返回值，其余情况置空。
     * <p>输出 Map 以端口名（portName）为 key，与数据边的 sourceParameterId 对齐。</p>
     */
    private Map<String, Object> bindOutputs(CompositeNodePlanBO nodePlan, Object result) {
        Map<String, Object> outputs = new LinkedHashMap<>();
        Map<String, Object> fields = toFieldMap(result);
        boolean singlePort = nodePlan.getOutputParams().size() == 1;
        for (Map.Entry<String, String> port : nodePlan.getOutputParams().entrySet()) {
            String name = port.getValue();
            if (fields != null && fields.containsKey(name)) {
                outputs.put(name, fields.get(name));
            } else {
                outputs.put(name, singlePort ? result : null);
            }
        }
        return outputs;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toFieldMap(Object result) {
        if (result == null) {
            return null;
        }
        if (result instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (result instanceof List<?> || result instanceof CharSequence || result instanceof Number
                || result instanceof Boolean || result instanceof Enum<?>) {
            return null;
        }
        try {
            Object converted = objectMapper.convertValue(result, Map.class);
            return converted instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
        } catch (Exception e) {
            return null;
        }
    }
}
