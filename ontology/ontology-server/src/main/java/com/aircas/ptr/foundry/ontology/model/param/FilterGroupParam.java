package com.aircas.ptr.foundry.ontology.model.param;


import com.aircas.ptr.foundry.ontology.model.enums.LogicEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.List;


@ApiModel(description = "嵌套过滤请求")
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class FilterGroupParam {

    @ApiModelProperty(name = "logic", value = " AND/OR，默认 AND ", example = "AND")
    private LogicEnum logic = LogicEnum.AND;

    /** 子节点：要么是 Filter，要么是 子Group */
    @ApiModelProperty(name = "children", value = "过滤子节点，支持嵌套")
    private List<FilterNodeParam> children;
}
