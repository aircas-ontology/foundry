package com.aircas.ptr.foundry.ontology.model.dto;


import com.aircas.ptr.foundry.ontology.model.param.ActionLinkMappingParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionTestListReadRuleParam;
import com.aircas.ptr.foundry.ontology.model.param.EntityActionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.po.OntologyProperty;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
public class ActionContextInfoDTO {


    private List<FunctionParameterVO> functionInputParams;

    private List<ActionParamMappingVO> mappings;

    private Map<String, List<Object>> entityDetailMap;

    /**
     * 按算子入参单独加载的实例数据：入参名 -> (属性id -> 值)。
     * <p>只有请求里 inputParams 点到的入参有条目，用于同一对象的两个属性入参读不同实例；
     * 其余入参继续用 entityDetailMap 的共用视图。异步回调时随上下文一起序列化。</p>
     */
    private Map<String, Map<String, List<Object>>> paramEntityDetailMaps;

    /**
     * LIST 入参按元素给的数据位置：入参名 -> 按元素顺序排列的属性取值（属性id -> 单个值）。
     * <p>同一份列表的元素可以来自不同对象、不同实例，不能按列对齐拼装，元素字段只取该元素自己那个对象的属性。
     * 只有请求里 inputElementParams 点到的入参有条目；异步回调时随上下文一起序列化。</p>
     */
    private Map<String, List<Map<String, Object>>> paramElementDetailMaps;

    /**
     * 行为节点上配了取值来源的入参取值：参数名 -> 值，取值时优先于该绑定行自己的来源。
     * <p>来源是黑板变量或上游行为出参；按参数名覆盖，所以属性来源的绑定行也一样生效。
     * 异步回调时随上下文一起序列化。</p>
     */
    private Map<String, Object> paramValueOverrides;

    /**
     * 来源为「外部输入」的参数取值（参数名 -> 值），只有行为测试接口会传入；定时执行为空
     */
    private Map<String, Object> externalParams;

    /**
     * 对象模式测试配的子表读取规则：算子入参名 -&gt; 规则。
     * <p>只影响该入参自己的读取视图（行数、排序属性与方向），其余入参仍按原口径取；
     * 异步回调时随上下文一起序列化。</p>
     */
    private Map<String, ActionTestListReadRuleParam> listReadRules;

    /**
     * 本次算子调用真正解析出的入参（顶层入参名 -> 值）：调用方给一个可变容器，{@code callFunction} 回填，
     * 供行为树成功条件表达式按 #inputs 读取；为空表示不收集（对外单行为测试不关心）。
     * <p>涉及关系的行为按对端逐条执行，回填时对容器加锁，最后完成的一条覆盖。</p>
     */
    private Map<String, Object> resolvedInputs;

    private OntologyActionDetailVO actionDetailVO;

    private FunctionDetailVO functionDetailVO;

    private List<OntologyProperty> ontologyProperties;

    /** 每个涉及对象选中的实例主键：对象id -> 实例主键；批量按实例遍历时为空，取单次传入的实体主键 */
    private Map<String, Object> entityPrimaryKeys;

    /** 涉及对象的属性定义：对象id -> 属性列表；出参按行所属对象回写时按它定位主键与数据源 */
    private Map<String, List<OntologyProperty>> objectProperties;

    private EntityActionExecuteParam entityActionExecuteParam;

    //关系涉及的相关参数
    private ActionLinkMappingParam link;

    private String linkToOntologyUniqueIdentifier;

    private Object linkEntityPrimaryKey;


}
