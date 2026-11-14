package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "行为调度详情VO")
public class ActionSchedulingDetailVO extends ActionSchedulingInfoVO {

    @Schema(name = "ruleVO", description = "规则调度详情：type=RULE 时返回，judgeRules 为保存的组合条件+行为参数，供前端回显重画")
    private ActionHandleRuleVO ruleVO;

    @Schema(name = "taskVO", description = "周期调度详情：type=TASK 时返回，cron/周期类型/执行参数")
    private ActionHandleTaskInfoVO taskVO;
}
