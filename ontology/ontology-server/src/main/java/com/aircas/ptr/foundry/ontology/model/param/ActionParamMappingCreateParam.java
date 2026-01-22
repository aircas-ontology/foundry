package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author yangj
 */
@ApiModel(description = "行为数据参数新增模型")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ActionParamMappingCreateParam {

    @ApiModelProperty(value = "参数对应的函数参数ID", required = true, example = "fdsfa-gfdsagf-dfs")
    private Long functionParamId;

    @ApiModelProperty(value = "行为对应的函数参数的表达式", required = true, example = "data.user[0].name")
    private String functionParamExpression;

    @ApiModelProperty(value = "参数对应的本体属性ID", required = true, example = "fdsfa-gfdsagf-dfs")
    private String propertyUniqueIdentifier;

    @ApiModelProperty(value = "参数对应的本体", required = true, example = "fdsfa-gfdsagf-dfs")
    private String ontologyUniqueIdentifier;
}
