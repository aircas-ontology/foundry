package com.aircas.ptr.foundry.ontology.composite;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.ontology.model.bo.CompositeFunctionRefBO;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionParamCategoryEnum;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.model.param.CompositeConfig;
import com.aircas.ptr.foundry.ontology.model.param.CompositeNode;
import com.aircas.ptr.foundry.ontology.model.param.CompositeParameter;
import com.aircas.ptr.foundry.ontology.model.po.Function;
import com.aircas.ptr.foundry.ontology.model.po.FunctionParamPO;
import com.aircas.ptr.foundry.ontology.utils.CompositeValidator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Component
@RequiredArgsConstructor
public class CompositeFunctionRefResolver {

    private final CompositeFunctionCatalog catalog;

    private static final ObjectMapper fieldMapper = new ObjectMapper();

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
        // 脚本算子（CUSTOMIZE）的 result 出参承载的是 FunctionResultVO.data，真实类型由用户代码决定，
        // 注册表统一声明成 OBJECT；这里按声明类型还原（见 declaredScriptTypes），
        // 基础查询/外部API 等结构化出参保持原样。
        boolean scriptOperator = function.getType() != null && function.getType().name().equals("CUSTOMIZE");
        List<CompositeParameter> inputs = toPorts(inputParams, FunctionParamCategoryEnum.INPUT);
        List<CompositeParameter> outputs = toPorts(outputParams, FunctionParamCategoryEnum.OUTPUT,
                scriptOperator ? declaredScriptTypes(function.getCode()) : Map.of());
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
     * 参数定义转画布端口。
     * <p>原型侧函数节点的端口 id 形如 {@code {函数id}-out-confidence}，但连线、外部输入输出映射引用的都是
     * 编排配置里落库的同一份 id，服务端按 id 精确匹配即可，这里用参数名作为 id 保证与 {@code name} 一致，
     * 使"按参数名连线"这一最常见写法成立。</p>
     */
    private List<CompositeParameter> toPorts(List<FunctionParamPO> params, FunctionParamCategoryEnum category) {
        return toPorts(params, category, Map.of());
    }

    /**
     * @param declaredTypes 出参端口的声明类型覆盖（参数名 -&gt; 画布类型），来自脚本注解；未命中的仍按注册表类型
     */
    private List<CompositeParameter> toPorts(List<FunctionParamPO> params, FunctionParamCategoryEnum category,
                                             Map<String, String> declaredTypes) {
        List<CompositeParameter> ports = new ArrayList<>();
        boolean flattenObject = category == FunctionParamCategoryEnum.INPUT;
        for (FunctionParamPO paramPO : params) {
            List<Map<String, Object>> nestedFields = parseFields(paramPO.getFields());
            if (flattenObject && paramPO.getParamType() == FunctionParamTypeEnum.OBJECT
                    && nestedFields != null && !nestedFields.isEmpty()) {
                // 入参 OBJECT 类型含嵌套字段时，展开为叶子字段端口（前端画布连线引用的是叶子字段名）
                addLeafPorts(ports, nestedFields, declaredTypes);
            } else {
                CompositeParameter port = new CompositeParameter();
                port.setId(paramPO.getParamName());
                port.setName(paramPO.getParamName());
                String declared = declaredTypes.get(paramPO.getParamName());
                port.setType(declared != null ? declared : canvasType(paramPO));
                port.setDescription(paramPO.getDescription());
                ports.add(port);
            }
        }
        return ports;
    }

    /** 解析 fields JSON 为嵌套字段列表 */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseFields(String fieldsJson) {
        if (fieldsJson == null || fieldsJson.trim().isEmpty()) {
            return null;
        }
        try {
            return fieldMapper.readValue(fieldsJson, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("函数字段定义解析失败: {}", fieldsJson, e);
            return null;
        }
    }

    /** 递归提取叶子字段端口：跳过有子字段的中间 OBJECT 节点，只保留叶子 */
    private void addLeafPorts(List<CompositeParameter> ports, List<Map<String, Object>> fields,
                              Map<String, String> declaredTypes) {
        for (Map<String, Object> field : fields) {
            String fieldName = field == null ? null : String.valueOf(field.get("paramName"));
            if (fieldName == null || fieldName.equals("null")) {
                continue;
            }
            String fieldType = String.valueOf(field.get("paramType"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) field.get("fields");
            if ("OBJECT".equalsIgnoreCase(fieldType) && children != null && !children.isEmpty()) {
                addLeafPorts(ports, children, declaredTypes);
            } else {
                CompositeParameter port = new CompositeParameter();
                port.setId(fieldName);
                port.setName(fieldName);
                String declared = declaredTypes.get(fieldName);
                port.setType(declared != null ? declared : canvasTypeFromJavaType(fieldType));
                ports.add(port);
            }
        }
    }

    /** 将 Java 参数类型名转为画布端口类型（与 CompositeFunctionCatalog.toCanvasType 对齐） */
    private static String canvasTypeFromJavaType(String javaType) {
        if (javaType == null || javaType.trim().isEmpty()) {
            return "object";
        }
        return switch (javaType.trim().toUpperCase()) {
            case "STRING" -> "string";
            case "INTEGER", "INT", "FLOAT", "DOUBLE", "LONG", "SHORT", "BYTE", "NUMBER" -> "number";
            case "BOOLEAN", "BOOL" -> "boolean";
            case "LIST", "ARRAY" -> "array";
            case "OBJECT" -> "object";
            default -> "object";
        };
    }

    /**
     * 解析脚本源码里的出参类型注解，还原 result 等 OBJECT 出参的真实类型。
     * <p>支持三种写法：Python 风格 {@code # @result:number}、Groovy/JS 行注释
     * {@code // @result:number}、以及块注释里的同名标记。注解只影响类型展示与连线比较，
     * 不写注解时维持 OBJECT（需要下游加转换节桥接）。</p>
     */
    static Map<String, String> declaredScriptTypes(String code) {
        Map<String, String> types = new LinkedHashMap<>();
        if (code == null || code.trim().isEmpty()) {
            return types;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("@([A-Za-z_]\\w*)\\s*:\\s*(string|number|boolean|object|array|list|integer|int|float|double|long)",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(code);
        while (matcher.find()) {
            types.put(matcher.group(1), normalizeCanvasType(matcher.group(2)));
        }
        return types;
    }

    private static String normalizeCanvasType(String raw) {
        switch (raw.toLowerCase()) {
            case "integer", "int", "float", "double", "long":
                return "number";
            case "list":
                return "array";
            default:
                return raw.toLowerCase();
        }
    }

    /**
     * 参数定义转画布端口（默认按注册表类型）。
     */
    private static String canvasType(FunctionParamPO param) {
        return CompositeFunctionCatalog.toCanvasType(param.getParamType());
    }
}
