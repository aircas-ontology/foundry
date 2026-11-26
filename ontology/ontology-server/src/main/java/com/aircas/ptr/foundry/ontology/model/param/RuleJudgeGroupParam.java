package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.RuleMonitorTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "规则调度单条规则：组合条件判断（可整体取反）+ 命中后执行的行为。"
        + "先选事件类型（新增/修改/删除），再选对象+属性+输入值做比较判断，"
        + "命中的事件决定是否触发行为执行。条件项之间按 linkFlag 用 AND/OR 组合。"
        + "事件类型可整组选一次（monitorType 写在组上），也可逐条写在各条件项上（与组级同值时等价）。"
        + "新增/删除条件按事件行的属性值判断，修改条件按实例当前属性值判断；"
        + "规则的启用不触发新增/删除，只对启用后到达的事件生效")
public class RuleJudgeGroupParam {

    @Schema(name = "condition", description = "是否对组合条件的结果整体取反：true 时条件不成立才执行（else 语义）", example = "false")
    private Boolean condition;

    @Schema(name = "monitorType", description = "组级事件类型：新增 / 修改 / 删除（兼容旧值 insert/update/delete）。"
            + "选好事件类型后，组内条件项给出对象+属性+输入值即可，条件项自身 monitorType 可省略；"
            + "条件项自身 monitorType 非空时以条件项为准，但同组内与组级类型不一致会报错。"
            + "为空时按条件项各自的 monitorType 判定；整组都没类型则按属性比较条件（修改语义）处理", example = "新增")
    private RuleMonitorTypeEnum monitorType;

    @Schema(name = "judgeParams", description = "判断条件列表：括号 + AND/OR 组合", required = true)
    private List<RuleJudgeItemParam> judgeParams;

    @Schema(name = "actionParam", description = "条件成立（或取反后成立）时执行的行为参数", required = true)
    private RuleActionParam actionParam;
}