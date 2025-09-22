package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author yangj
 */
@ApiModel(description = "行为数据参数新增模型")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ActionMappingInAddParam {

    @ApiModelProperty(value = "参数名称", required = true, example = "mbbh")
    private String parameterName;

    @ApiModelProperty(value = "参数对应的本体属性ID", required = true, example = "fdsfa-gfdsagf-dfs")
    private String propertyUniqueIdentifier;

    @ApiModelProperty(value = "参数对应的本体", required = true, example = "fdsfa-gfdsagf-dfs")
    private String ontologyUniqueIdentifier;
}
