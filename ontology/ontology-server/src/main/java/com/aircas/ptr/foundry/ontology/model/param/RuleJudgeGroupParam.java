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
        + "条件可跨任意对象实例；条件项既可以是属性比较，也可以是监测条件（选对象+存储分组）。"
        + "监测用法先在本组 monitorType 选事件类型（insert/update/delete），再添加条件项，"
        + "条件项之间按 linkFlag 用 AND/OR 组合；也可把 monitorType 写在每个条件项上（兼容写法，与组级同值时等价）。"
        + "属性条件只在条件引用的实例发生变化时定向评估；监测条件在对应分组数据增删改的 CDC 事件到达时求值，"
        + "多个监测条件用 AND 时按同一实例的事件在累计窗口内凑齐才触发，"
        + "规则启用时不对监测条件做存量评估")
public class RuleJudgeGroupParam {

    @Schema(name = "condition", description = "是否对组合条件的结果整体取反：true 时条件不成立才执行（else 语义）", example = "false")
    private Boolean condition;

    @Schema(name = "monitorType", description = "组级监测类型：insert 新增 / update 更新 / delete 删除。"
            + "先选事件类型再添加条件时填这里，组内条件项只需给对象+存储分组，事项自身 monitorType 可省略；"
            + "条件项自身 monitorType 非空时以条件项为准，但同组内与组级类型不一致会报错。"
            + "为空时按条件项各自的 monitorType 判定（兼容旧写法）；整组都没类型则按属性比较条件处理", example = "insert")
    private RuleMonitorTypeEnum monitorType;

    @Schema(name = "judgeParams", description = "判断条件列表：括号 + AND/OR 组合", required = true)
    private List<RuleJudgeItemParam> judgeParams;

    @Schema(name = "actionParam", description = "条件成立（或取反后成立）时执行的行为参数", required = true)
    private RuleActionParam actionParam;
}