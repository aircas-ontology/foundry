package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Accessors(chain = true)
@ApiModel(description = "行为参数映射VO")
public class ActionParamMappingVO {

    @ApiModelProperty(value = "关联的函数参数值的spel表达式", required = true, example = "data[0].name")
    private String functionParamExpression;

    @ApiModelProperty(value = "关联的函数参数id", required = true)
    private Long functionParamId;


    @ApiModelProperty(value = "属性关联本体id", required = true, example = "abcd1234")
    private String ontologyUniqueIdentifier;

    @ApiModelProperty(value = "参数对应的本体属性ID", required = true, example = "fdsfa-gfdsagf-dfs")
    private String propertyUniqueIdentifier;

}
