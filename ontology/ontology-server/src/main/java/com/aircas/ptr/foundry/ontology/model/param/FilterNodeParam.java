package com.aircas.ptr.foundry.ontology.model.param;


import com.aircas.ptr.foundry.ontology.model.enums.FilterNodeTypeEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@ApiModel(description = "过滤节点请求")
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class FilterNodeParam {

    @ApiModelProperty(value = "过滤节点类型：FILTER/GROUP 二选一", example = "FILTER")
    private FilterNodeTypeEnum type;

    // 二选一
    @ApiModelProperty(name = "filter", value = "单过滤条件，当type=filter时必填")
    private PropertyFilterParam filter;

    @ApiModelProperty(name = "group", value = "嵌套过滤条件，当type=group时必填")
    private FilterGroupParam group;
}
