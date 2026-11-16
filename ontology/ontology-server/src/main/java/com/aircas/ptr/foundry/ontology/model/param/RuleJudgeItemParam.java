package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.ActionHandleRuleAddConditionEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ActionRuleConnectType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "规则判断条件项：左操作数 比较符 右操作数，可带括号，linkFlag 连接下一条。"
        + "求值时 AND 优先于 OR，括号可改变优先级")
public class RuleJudgeItemParam {

    @Schema(name = "leftOperate", description = "左操作数", required = true)
    private RuleOperateParam leftOperate;

    @Schema(name = "rightOperate", description = "右操作数", required = true)
    private RuleOperateParam rightOperate;

    @Schema(name = "condition", description = "比较操作符：LT/LE/EQ/NE/GT/GE（不支持 CH）", example = "GT", required = true)
    private ActionHandleRuleAddConditionEnum condition;

    @Schema(name = "isLeft", description = "本条件前是否加左括号", example = "false")
    private Boolean isLeft;

    @Schema(name = "isRight", description = "本条件后是否加右括号", example = "false")
    private Boolean isRight;

    @Schema(name = "linkFlag", description = "与下一条的连接符 AND/OR；最后一条不传", example = "AND")
    private ActionRuleConnectType linkFlag;
}