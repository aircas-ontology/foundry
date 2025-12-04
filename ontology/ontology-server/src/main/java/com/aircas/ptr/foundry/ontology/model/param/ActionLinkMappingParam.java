package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@ApiModel(description = "行为关系新增")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActionLinkMappingParam {

    @ApiModelProperty(value = "行为关联的关系id")
    private String ontologyLinkUniqIdentifier;

    @ApiModelProperty(value = "行为关联的函数参数表达式")
    private String ontologyLinkFunctionParamExpression;

}
