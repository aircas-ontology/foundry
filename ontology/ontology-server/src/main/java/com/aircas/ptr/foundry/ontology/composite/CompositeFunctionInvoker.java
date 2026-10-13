package com.aircas.ptr.foundry.ontology.composite;

import com.aircas.ptr.foundry.common.exception.BusinessException;
import com.aircas.ptr.foundry.ontology.model.bo.CompositeNodePlanBO;
import com.aircas.ptr.foundry.ontology.model.po.Function;
import com.aircas.ptr.foundry.ontology.model.po.FunctionParamPO;
import com.aircas.ptr.foundry.ontology.service.GroovyService;
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

    public CompositeFunctionInvoker(GroovyService groovyService, BasicQueryOperator basicQueryOperator,
                                    ExternalApiOperator externalApiOperator) {
        this.groovyService = groovyService;
        this.basicQueryOperator = basicQueryOperator;
        this.externalApiOperator = externalApiOperator;
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
            case CUSTOMIZE -> require(groovyService, "脚本算子执行引擎").invokeHandle(
                    function.getApi(), function.getCode(), paramMap, inputParams);
            case BASIC_QUERY -> require(basicQueryOperator, "基础函数算子执行器").runAsNode(
                    require(basicQueryOperator, "基础函数算子执行器")
                            .parseConfig(function.getCode(), function.getApi()), paramMap);
            case EXTERNAL -> require(externalApiOperator, "外部API算子执行器")
                    .runAsNode(function, nodePlan, paramMap);
            case COMPOSITE -> throw new BusinessException("编排节点[" + labelOf(nodePlan) + "]不能引用函数编排算子："
                    + function.getApi(), HttpStatus.BAD_REQUEST);
        };
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
            paramMap.put(paramPO.getParamName(), inputs.get(paramPO.getParamName()));
        }
        return paramMap;
    }

    private static String labelOf(CompositeNodePlanBO nodePlan) {
        return nodePlan == null ? "?" : nodePlan.getLabel();
    }
}
