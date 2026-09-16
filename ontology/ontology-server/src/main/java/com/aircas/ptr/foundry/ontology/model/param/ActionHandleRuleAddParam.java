package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.ActionHandleRuleAddConditionEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @className: ActionHandleRuleAddParam
 * @author: yangj
 * @date: 2024/9/1 18:53
 * @version: 1.0
 * @description: 行为执行规则添加参数
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为执行规则添加参数")
public class ActionHandleRuleAddParam {

    @Schema(description = "字段名", required = true, example = "longitude")
    private String columnName;

    @Schema(description = "字段值", required = true, example = "0.0")
    private String columnValue;

    @Schema(description = "字段判断类型", required = true, example = "EQ")
    private ActionHandleRuleAddConditionEnum condition;

    public String rule() {
        return columnName + condition.getCode() + columnValue;
    }
}
