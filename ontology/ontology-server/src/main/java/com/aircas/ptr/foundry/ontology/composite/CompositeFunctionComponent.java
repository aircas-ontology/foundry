package com.aircas.ptr.foundry.ontology.composite;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.model.bo.CompositeNodePlanBO;
import com.aircas.ptr.foundry.ontology.model.po.Function;
import com.aircas.ptr.foundry.ontology.model.po.FunctionParamPO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionResultVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

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
@Slf4j
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
        // 嵌套字段端口还原：前端画布把 OBJECT 入参的子字段拆成独立端口连线，
        // resolveInputs 取出的是扁平叶子值，这里按原始参数定义重建嵌套结构
        Map<String, Object> resolvedInputs = reconstructNestedInputs(inputs, inputParams);
        Object result = invoker.invoke(function, nodePlan, resolvedInputs, inputParams);
        return bindOutputs(nodePlan, unwrap(result));
    }

    /**
     * 将扁平的叶子端口值还原为嵌套的 OBJECT 入参结构。
     * <p>例如：inputs={"userName":"Alice","orderAmount":3}，inputParams 声明了
     * input(OBJECT) 含子字段 userName/orderAmount，则还原为
     * {"userName":"Alice","orderAmount":3,"input":{"userName":"Alice","orderAmount":3}}。</p>
     */
    private Map<String, Object> reconstructNestedInputs(Map<String, Object> inputs, List<FunctionParamPO> inputParams) {
        if (inputParams == null || inputParams.isEmpty()) {
            return inputs;
        }
        Map<String, Object> result = new LinkedHashMap<>(inputs);
        for (FunctionParamPO param : inputParams) {
            if (param.getFields() == null || param.getFields().trim().isEmpty()) {
                continue;
            }
            List<Map<String, Object>> fields;
            try {
                fields = objectMapper.readValue(param.getFields(), new TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception e) {
                log.warn("函数[{}]参数[{}]字段定义解析失败", param.getFunctionId(), param.getParamName(), e);
                continue;
            }
            if (fields == null || fields.isEmpty()) {
                continue;
            }
            Map<String, Object> nested = new LinkedHashMap<>();
            if (collectLeafValues(fields, inputs, nested)) {
                result.put(param.getParamName(), nested);
            }
        }
        return result;
    }

    /** 递归收集叶子字段值，返回是否命中了至少一个叶子 */
    @SuppressWarnings("unchecked")
    private boolean collectLeafValues(List<Map<String, Object>> fields, Map<String, Object> inputs,
                                      Map<String, Object> target) {
        boolean found = false;
        for (Map<String, Object> field : fields) {
            String name = field == null ? null : String.valueOf(field.get("paramName"));
            if (name == null || "null".equals(name)) {
                continue;
            }
            List<Map<String, Object>> children = (List<Map<String, Object>>) field.get("fields");
            if (children != null && !children.isEmpty()) {
                Map<String, Object> childTarget = new LinkedHashMap<>();
                if (collectLeafValues(children, inputs, childTarget)) {
                    target.put(name, childTarget);
                    found = true;
                }
            } else if (inputs.containsKey(name)) {
                target.put(name, inputs.get(name));
                found = true;
            }
        }
        return found;
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
