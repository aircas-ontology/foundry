package com.aircas.ptr.foundry.ontology.model.param;


import com.aircas.ptr.foundry.ontology.model.enums.FilterNodeTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Schema(description = "过滤节点请求")
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class FilterNodeParam {

    @Schema(description = "过滤节点类型：FILTER/GROUP 二选一", example = "FILTER")
    private FilterNodeTypeEnum type;

    // 二选一
    @Schema(name = "filter", description = "单过滤条件，当type=filter时必填")
    private PropertyFilterParam filter;

    @Schema(name = "group", description = "嵌套过滤条件，当type=group时必填")
    private FilterGroupParam group;
}
