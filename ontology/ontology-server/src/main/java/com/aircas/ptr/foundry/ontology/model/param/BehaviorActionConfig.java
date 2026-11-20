package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * 行为节点的配置。
 * <p>节点决定绑哪个行为、重试几次，以及要不要把某个入参的取值改成黑板变量或上游节点的出参、
 * 要不要把某条出参回传进黑板。行为需要的实例主键、分组表行主键、关系对端主键仍全部由执行请求
 * 按节点传（见 TreeExecuteParam.nodeExecutes），节点上不配这些数据。
 * 绑定是逐参数、逐出参的白名单：没配的入参照旧按行为声明的来源取值、照常接受执行请求传值。
 * 失败重试在单次执行内同步进行，重试会重复落库，只应对可重复执行的行为开；
 * 超时无法安全中断已经开始的行为调用，不再支持，需要限制后续步骤请用限时节点。</p>
 * <p>{@link #successCondition} 不配就是原口径：行为调用没报错就算成功。配了才算多加一道判定，
 * 条件不成立按失败处理，出参不回传黑板，下游引用不到这次的值。</p>
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树行为节点配置")
public class BehaviorActionConfig {

    @Schema(name = "actionApi", description = "绑定的行为 api", example = "act.satellite.track")
    private String actionApi;

    @Schema(name = "timeoutSeconds", description = "已不支持：单轮同步执行无法中断已经开始的行为，配正数会被拒绝；0 或空表示不限制；限制后续步骤请改用限时节点")
    private Integer timeoutSeconds;

    @Schema(name = "retryTimes", description = "失败重试次数，0 或空表示不重试；重试会重复落库，只应对可重复执行的行为开启")
    private Integer retryTimes;

    @Schema(name = "inputBindings", description = "入参取值绑定：入参名 -> 这一路的取值来源。来源要么给 blackboardKey"
            + "（取本树黑板键的当前值），要么给 nodeId + output（取上游行为节点的出参），两者只能填一种。"
            + "LIST 入参取上游出参时什么形状都收：选取值路径带 [*] 的出参行拿整批列，或选类型为 LIST（路径直接指向数组）的出参行拿整个数组，"
            + "单值出参按只有一个元素的集合传。"
            + "没配的入参照旧按行为声明的来源取值（外部输入由执行请求给、对象属性按实例读、固定值取定义）")
    private Map<String, BehaviorInputBinding> inputBindings;

    @Schema(name = "outputBindings", description = "出参回传绑定：出参标识 -> 回传到的黑板键名。本节点执行成功后，"
            + "按该出参绑定行的取值路径从返回体取值写进那个键，供后面的节点或条件使用，与出参落库互不影响："
            + "原来写回表的照常写回表，之后再把值写进黑板变量。"
            + "出参标识写法与出参写回同一套：属性唯一标识 > 出参字段名 > 顶层算子出参名；"
            + "键是外部输入来源的黑板键，或绑定了该出参写回属性的对象属性来源黑板键")
    private Map<String, String> outputBindings;

    @Schema(name = "successCondition", description = "行为执行成功条件：可选，不传就是行为调用没报错算成功。"
            + "传了就写一条 SpEL 表达式，自己算完给 true/false。成立该节点才算成功；"
            + "不成立、或条件判不了都按失败处理（会走本节点的重试），且这次出参不回传黑板")
    private SuccessCondition successCondition;

    /**
     * 一条成功条件：只读求值，必须返回 true/false。
     * <p>变量 #result 是本次完整返回体、#data 是返回体里的业务数据层
     * （没有 data 时同 #result）、#inputs 是本次真正传给算子的入参（顶层入参名 -> 值，LIST 入参可按下标取某一项）。
     * 不走黑板也不回库——黑板上的值要到本节点成功之后才会被这次结果刷新，拿旧值判本节点会把判错的轮次当成成功。</p>
     */
    @Data
    @SuperBuilder
    @AllArgsConstructor
    @NoArgsConstructor
    @Schema(description = "行为节点的执行成功条件")
    public static class SuccessCondition {

        @Schema(name = "expression", description = "SpEL 表达式式成功条件。"
                + "在只读上下文里求值，必须返回 true/false：true 算成功，false 算条件不成立，"
                + "求值异常、字段取不到或结果不是布尔算判不了，同样按失败处理。"
                + "可用变量：#result 本次返回体（完整外壳）、#data 返回体里的业务数据层（没有 data 时同 #result）、"
                + "#inputs 本次真正传给算子的入参（顶层入参名 -> 值；LIST 入参按整数下标取某一项，如 #inputs.trajectories[0].longitude）。"
                + "集合判空、取第几条、对取到的值做运算都可以直接写在表达式里",
                example = "#data.PredictedPoint.altitude > 0 && #inputs.trajectories.size() > 1")
        private String expression;
    }
}
