package com.aircas.ptr.foundry.ontology.composite;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.ontology.model.bo.CompositeFunctionRefBO;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamCategoryEnum;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.model.param.BasicQueryConfig;
import com.aircas.ptr.foundry.ontology.model.param.CompositeConfig;
import com.aircas.ptr.foundry.ontology.model.param.CompositeNode;
import com.aircas.ptr.foundry.ontology.model.param.CompositeParameter;
import com.aircas.ptr.foundry.ontology.model.po.Function;
import com.aircas.ptr.foundry.ontology.model.po.FunctionParamPO;
import com.aircas.ptr.foundry.ontology.utils.CompositeValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 函数编排引用解析器。
 * <p>{@code CompositeValidator} 与 {@code CompositeElCompiler} 需要两类视角的被引用函数信息：</p>
 * <ul>
 *   <li>按<b>画布节点 id</b> 索引的 {@code Map}（校验器端口推导与编译器的 {@code refs} 参数）；</li>
 *   <li>按<b>函数 api</b> 求值的 {@code java.util.function.Function}（校验器的 {@code resolver} 参数，
 *       校验器传入的是节点的 {@code functionId}）。</li>
 * </ul>
 * <p>两者的键不同，容易混用，因此这里一次性提供两个视图，并把函数入参/出参定义转换成画布端口类型。</p>
 */
@Component
@RequiredArgsConstructor
public class CompositeFunctionRefResolver {

    private final CompositeFunctionCatalog catalog;

    /** 基础查询算子：其画布端口的约定名与形态在此统一推导，与创建时落库的 function_param 解耦 */
    private final BasicQueryOperator basicQueryOperator;

    /**
     * 建立"画布节点 id -&gt; 被引用函数快照"的映射。
     * <p>同一个函数被多个节点引用时只查库一次。</p>
     */
    public Map<String, CompositeFunctionRefBO> resolveByCanvasNodeId(CompositeConfig config) {
        Map<String, CompositeFunctionRefBO> refs = new LinkedHashMap<>();
        if (config == null) {
            return refs;
        }
        Map<String, CompositeFunctionRefBO> byApi = new LinkedHashMap<>();
        for (CompositeNode node : config.getNodes()) {
            if (node == null || node.getId() == null || node.getFunctionId() == null
                    || node.getFunctionId().trim().isEmpty()) {
                continue;
            }
            if (CompositeValidator.isLogicNode(node)) {
                continue;
            }
            String api = node.getFunctionId().trim();
            // computeIfAbsent 不会缓存 null，函数不存在时每次都会重新查库，这里显式记忆未命中结果
            CompositeFunctionRefBO ref;
            if (byApi.containsKey(api)) {
                ref = byApi.get(api);
            } else {
                ref = loadByApi(api);
                byApi.put(api, ref);
            }
            if (ref != null) {
                refs.put(node.getId(), ref);
            }
        }
        return refs;
    }

    /** 校验器使用的解析器：按函数 api 取快照，不存在返回 null */
    public java.util.function.Function<String, CompositeFunctionRefBO> byFunctionApi() {
        Map<String, CompositeFunctionRefBO> cache = new LinkedHashMap<>();
        return api -> {
            if (api == null || api.trim().isEmpty()) {
                return null;
            }
            String key = api.trim();
            // 未命中同样要记忆，否则每次校验都重复查库
            if (cache.containsKey(key)) {
                return cache.get(key);
            }
            CompositeFunctionRefBO ref = loadByApi(key);
            cache.put(key, ref);
            return ref;
        };
    }

    /** 查询函数定义并组装端口信息，函数不存在返回 null */
    private CompositeFunctionRefBO loadByApi(String functionApi) {
        Function function = catalog.findFunction(functionApi);
        if (function == null) {
            return null;
        }
        List<FunctionParamPO> inputParams = catalog.findParams(function.getId(), FunctionParamCategoryEnum.INPUT);
        List<FunctionParamPO> outputParams = catalog.findParams(function.getId(), FunctionParamCategoryEnum.OUTPUT);
        List<CompositeParameter> inputs = toPorts(inputParams);
        List<CompositeParameter> outputs = toPorts(outputParams);
        // 基础查询算子的「本体标识」入参与「结果」出参属于算子内在校识，已不再写入 function_param，
        // 这里按查询配置动态补齐，保证画布端口、连线校验与单独创建时一致。
        if (function.getType() == FunctionTypeEnum.BASIC_QUERY) {
            inputs.add(0, port(BasicQueryOperator.PARAM_ONTOLOGY_IDENTIFIER,
                    FunctionParamTypeEnum.STRING, "查询目标本体标识"));
            outputs = basicQueryOutputPorts(function);
        }
        return CompositeFunctionRefBO.builder()
                .functionApi(function.getApi())
                .displayName(function.getDisplayName() == null ? function.getApi() : function.getDisplayName())
                .published(function.getStatus() != null && function.getStatus() == Status.ENABLE.getValue())
                .version(function.getReferenceName())
                .inputs(inputs)
                .outputs(outputs)
                .build();
    }

    /**
     * 基础查询算子的输出端口：与 {@link BasicQueryOperator#runAsNode} 返回值键一一对应，
     * 聚合/查询两种模式端口集不同，根据 code 里的 queryConfig 推导。
     */
    private List<CompositeParameter> basicQueryOutputPorts(Function function) {
        List<CompositeParameter> ports = new ArrayList<>();
        BasicQueryConfig config = basicQueryOperator.parseConfig(function.getCode(), function.getApi());
        if (config != null && config.getAggFunc() != null) {
            // 聚合模式：result/value 为聚合值，另附聚合函数、别名与命中的实际属性名
            ports.add(port(BasicQueryOperator.PORT_RESULT, FunctionParamTypeEnum.DOUBLE, "聚合结果"));
            ports.add(port(BasicQueryOperator.PORT_VALUE, FunctionParamTypeEnum.DOUBLE, "聚合值"));
            ports.add(port("aggFunc", FunctionParamTypeEnum.STRING, "聚合函数"));
            ports.add(port("alias", FunctionParamTypeEnum.STRING, "聚合别名"));
            ports.add(port("targetProperty", FunctionParamTypeEnum.STRING, "聚合目标属性"));
        } else {
            // 查询模式：result/records 为分页记录，total 为总数
            ports.add(port(BasicQueryOperator.PORT_RESULT, FunctionParamTypeEnum.LIST, "查询结果记录"));
            ports.add(port(BasicQueryOperator.PORT_RECORDS, FunctionParamTypeEnum.LIST, "分页记录"));
            ports.add(port(BasicQueryOperator.PORT_TOTAL, FunctionParamTypeEnum.LONG, "记录总数"));
        }
        return ports;
    }

    /** 构造一个画布端口（id 与 name 同参数名，保证“按参数名连线”成立）。 */
    private CompositeParameter port(String name, FunctionParamTypeEnum paramType, String description) {
        CompositeParameter port = new CompositeParameter();
        port.setId(name);
        port.setName(name);
        port.setType(CompositeFunctionCatalog.toCanvasType(paramType));
        port.setDescription(description);
        return port;
    }

    /**
     * 参数定义转画布端口。
     * <p>原型侧函数节点的端口 id 形如 {@code {函数id}-out-confidence}，但连线、外部输入输出映射引用的都是
     * 编排配置里落库的同一份 id，服务端按 id 精确匹配即可，这里用参数名作为 id 保证与 {@code name} 一致，
     * 使"按参数名连线"这一最常见写法成立。</p>
     */
    private List<CompositeParameter> toPorts(List<FunctionParamPO> params) {
        List<CompositeParameter> ports = new ArrayList<>();
        for (FunctionParamPO paramPO : params) {
            CompositeParameter port = new CompositeParameter();
            port.setId(paramPO.getParamName());
            port.setName(paramPO.getParamName());
            port.setType(CompositeFunctionCatalog.toCanvasType(paramPO.getParamType()));
            port.setDescription(paramPO.getDescription());
            ports.add(port);
        }
        return ports;
    }
}
