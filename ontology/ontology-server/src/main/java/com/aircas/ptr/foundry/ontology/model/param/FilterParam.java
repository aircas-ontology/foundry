package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.common.constant.ActionHandleRuleAddConditionEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@ApiModel(description = "查询条件参数")
@AllArgsConstructor
public class FilterParam {

    @ApiModelProperty(value = "查询键", required = true, example = "mbbh")
    private String filterKey;

    @ApiModelProperty(value = "查询值", required = true, example = "7")
    private String filterValue;

    @ApiModelProperty(value = "字段判断类型", required = false, example = "EQ")
    private ActionHandleRuleAddConditionEnum condition;

    @Override
    public String toString() {

        return "\"" + filterKey + "\"" + condition.getCode() + "'" + filterValue + "'";
    }
}
