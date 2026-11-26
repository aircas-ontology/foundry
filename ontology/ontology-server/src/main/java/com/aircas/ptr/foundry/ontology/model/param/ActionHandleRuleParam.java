package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Schema(description = "行为规则请求：规则数组，每条规则 = 组合条件（judgeParams，括号+AND/OR，可整体取反）+ 行为执行参数（actionParam）。"
        + "每条条件先选事件类型（新增/修改/删除），再选对象+属性+输入值做比较；"
        + "事件类型可整组选一次（monitorType 写在组上），也可逐条写在条件项上，条件项之间用 AND/OR 组合。"
        + "新增/删除按事件行属性值判断，修改按实例当前属性值判断；规则启用不触发新增/删除，只对启用后的事件生效")
public class ActionHandleRuleParam {

    @NotEmpty(message = "judgeRules is empty")
    @Schema(name = "judgeRules", description = "规则数组：条件可跨任意对象实例，CDC 只在条件引用的实例变化时定向评估；"
            + "规则启用时先对绑定实例评估一遍存量数据", required = true)
    private List<RuleJudgeGroupParam> judgeRules;

    @Schema(name = "flowDocument", description = "前端规则画布文档 JSON 字符串（前端自行序列化），后端只存储，仅查看调度详情时原样返回用于回显画布")
    private String flowDocument;
}