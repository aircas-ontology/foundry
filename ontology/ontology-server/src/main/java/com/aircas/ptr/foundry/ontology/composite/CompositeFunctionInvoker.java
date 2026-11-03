package com.aircas.ptr.foundry.ontology.composite;

import com.aircas.ptr.foundry.common.constant.FunctionParamTypeEnum;
import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.engine.ScriptEngineRouter;
import com.aircas.ptr.foundry.ontology.model.bo.CompositeNodePlanBO;
import com.aircas.ptr.foundry.ontology.model.enums.FunctionLanguageEnum;
import com.aircas.ptr.foundry.ontology.model.po.Function;
import com.aircas.ptr.foundry.ontology.model.po.FunctionParamPO;
import com.aircas.ptr.foundry.ontology.service.GroovyService;
import com.aircas.ptr.foundry.ontology.utils.SchemaHandleUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 编排节点的算子调用器：按被引用算子的类型分发执行。
 *
 * <p>函数编排画布上的"函数节点"可以引用三类算子，三类算子都能接入编排工作流：</p>
 * <ul>
 *   <li>{@code CUSTOMIZE} 脚本函数算子：调用 Groovy {@code handle} 方法；</li>
 *   <li>{@code BASIC_QUERY} 基础函数算子：执行本体属性通用查询；</li>
 *   <li>{@code EXTERNAL} 外部API函数算子：按接口定义发起 HTTP 调用。</li>
 * </ul>
 *
 * <p>此前编排节点无条件调用 Groovy，基础算子与外部API算子会被当成脚本代码执行而无法接入编排，
 * 因此把"用哪个引擎调用"从节点组件中抽出来，节点组件只负责取数与端口绑定。</p>
 */
public class CompositeFunctionInvoker {

    private final GroovyService groovyService;

    private final BasicQueryOperator basicQueryOperator;

    private final ExternalApiOperator externalApiOperator;

    private final ScriptEngineRouter scriptEngineRouter;

    public CompositeFunctionInvoker(GroovyService groovyService, BasicQueryOperator basicQueryOperator,
                                    ExternalApiOperator externalApiOperator, ScriptEngineRouter scriptEngineRouter) {
        this.groovyService = groovyService;
        this.basicQueryOperator = basicQueryOperator;
        this.externalApiOperator = externalApiOperator;
        this.scriptEngineRouter = scriptEngineRouter;
    }

    /**
     * 执行算子并返回业务结果。
     *
     * <p>脚本算子沿用 {@code FunctionResultVO} 结构（由节点组件剥离外壳）；
     * 基础算子与外部API算子返回"端口名 -&gt; 值"的键值对，便于节点组件按端口绑定。</p>
     *
     * @param function    被引用的算子定义
     * @param nodePlan    编排节点运行期定义
     * @param inputs      解析后的节点入参（入参名 -&gt; 值）
     * @param inputParams 算子入参定义（按声明顺序）
     * @return 算子返回值
     */
    public Object invoke(Function function, CompositeNodePlanBO nodePlan, Map<String, Object> inputs,
                         List<FunctionParamPO> inputParams) {
        if (function.getType() == null) {
            throw new BusinessException("编排节点[" + labelOf(nodePlan) + "]引用的函数缺少类型定义：" + function.getApi(),
                    HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> paramMap = assembleParams(inputParams, inputs);
        return switch (function.getType()) {
            case CUSTOMIZE -> executeScriptFunction(function, paramMap, inputParams);
            case BASIC_QUERY -> require(basicQueryOperator, "基础函数算子执行器").runAsNode(
                    require(basicQueryOperator, "基础函数算子执行器")
                            .parseConfig(function.getCode(), function.getApi()), paramMap);
            case EXTERNAL -> require(externalApiOperator, "外部API算子执行器")
                    .runAsNode(function, nodePlan, paramMap);
            case COMPOSITE -> throw new BusinessException("编排节点[" + labelOf(nodePlan) + "]不能引用函数编排算子："
                    + function.getApi(), HttpStatus.BAD_REQUEST);
            case CONTAINER -> throw new BusinessException("编排节点[" + labelOf(nodePlan) + "]引用的容器算子执行能力尚未开放："
                    + function.getApi(), HttpStatus.BAD_REQUEST);
        };
    }

    /**
     * 执行脚本算子：根据 language 字段路由到对应执行器（Groovy/TypeScript/Python/Java）。
     * <p>language 为 null 时从代码内容推断（兼容旧数据）。</p>
     */
    private Object executeScriptFunction(Function function, Map<String, Object> paramMap,
                                         List<FunctionParamPO> inputParams) {
        FunctionLanguageEnum language = function.getLanguage();
        if (language == null) {
            language = inferLanguageFromCode(function.getCode());
        }
        // 单对象入参解包：与非编排路径（FunctionServiceImpl#doExecuteFunction）对齐，
        // 只有一个入参时直接传值，避免 runner 端收到 {"input": 10} 而脚本期望 10
        String inputJson;
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            if (inputParams.size() == 1 && paramMap.containsKey(inputParams.get(0).getParamName())) {
                inputJson = objectMapper.writeValueAsString(paramMap.get(inputParams.get(0).getParamName()));
            } else {
                inputJson = objectMapper.writeValueAsString(paramMap);
            }
        } catch (Exception e) {
            throw new BusinessException("脚本算子入参序列化失败：" + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        com.aircas.ptr.foundry.ontology.engine.ScriptExecutionContext ctx =
                com.aircas.ptr.foundry.ontology.engine.ScriptExecutionContext.builder()
                        .language(language)
                        .functionApi(function.getApi())
                        .version(function.getVersion())
                        .code(function.getCode())
                        .inputJson(inputJson)
                        .inputMap(paramMap)
                        .inputParams(inputParams)
                        .build();
        String resultJsonStr = scriptEngineRouter.route(language).execute(ctx);
        // 解析结果：脚本返回 FunctionResultVO 结构，取 data 字段
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            var resultNode = objectMapper.readTree(resultJsonStr);
            if (resultNode != null && resultNode.has("data")) {
                return objectMapper.readValue(resultNode.get("data").traverse(), Object.class);
            }
            return objectMapper.readValue(resultJsonStr, Object.class);
        } catch (Exception e) {
            throw new BusinessException("脚本算子返回值解析失败：" + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 从代码内容推断脚本语言（兼容旧数据 language 为 null）。
     */
    private FunctionLanguageEnum inferLanguageFromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return FunctionLanguageEnum.GROOVY;
        }
        String trimmed = code.trim();
        if (trimmed.contains("export function") || trimmed.contains("export async function")
                || trimmed.matches("(?s).*\\binterface\\s+\\w+.*")
                || trimmed.matches("(?s).*:\\s*(number|string|boolean|any|unknown)\\b.*")) {
            return FunctionLanguageEnum.TYPESCRIPT;
        }
        if (trimmed.matches("(?s).*\\bdef\\s+handle\\s*\\(.*")
                || trimmed.matches("(?s).*\\bfrom\\s+\\w+\\s+import\\b.*")
                || trimmed.matches("(?s).*\\bimport\\s+\\w+\\b.*")) {
            return FunctionLanguageEnum.PYTHON;
        }
        if (trimmed.matches("(?s).*\\bpackage\\s+[\\w.]+;.*")
                || trimmed.matches("(?s).*\\bpublic\\s+class\\s+\\w+.*")) {
            return FunctionLanguageEnum.JAVA;
        }
        return FunctionLanguageEnum.GROOVY;
    }

    /** 算子执行器缺失时给出可读文案，而不是空指针 */
    private static <T> T require(T operator, String name) {
        if (operator == null) {
            throw new BusinessException("当前环境未接入" + name + "，无法执行该类型算子节点", HttpStatus.BAD_REQUEST);
        }
        return operator;
    }

    /** 按声明顺序组装算子入参表，未取到值的入参依然占位 */
    static Map<String, Object> assembleParams(List<FunctionParamPO> inputParams, Map<String, Object> inputs) {
        Map<String, Object> paramMap = new LinkedHashMap<>();
        for (FunctionParamPO paramPO : inputParams) {
            paramMap.put(paramPO.getParamName(),
                    coerce(inputs.get(paramPO.getParamName()), paramPO));
        }
        return paramMap;
    }

    /**
     * 把编排解析出的值转成算子声明的类型。
     * <p>与 {@code FunctionServiceImpl#buildInputJson} 一致：转换只在系统边界（调用算子之前）做一次，
     * 编排内部的端口值保持原样透传。缺失 Java 类名时按参数枚举类型名兜底（前端声明的参数只有枚举）；
     * 转换失败也按原值透传，让"常量写成字符串"这类配置照常工作。</p>
     */
    private static Object coerce(Object value, FunctionParamPO paramPO) {
        if (value == null || paramPO.getParamType() == FunctionParamTypeEnum.OBJECT) {
            return value;
        }
        String typeReference = paramPO.getTypeReferenceName();
        if (typeReference == null || typeReference.trim().isEmpty()) {
            // 枚举的 vale 是简单类名（Integer/Boolean/List...），补成全限定名后才能定位目标类型
            typeReference = "java.lang." + paramPO.getParamType().getVale();
        }
        try {
            return SchemaHandleUtil.convertValue(value, typeReference);
        } catch (RuntimeException e) {
            return value;
        }
    }

    private static String labelOf(CompositeNodePlanBO nodePlan) {
        return nodePlan == null ? "?" : nodePlan.getLabel();
    }
}
