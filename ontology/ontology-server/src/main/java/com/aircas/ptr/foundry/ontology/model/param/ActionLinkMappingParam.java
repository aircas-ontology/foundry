package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "行为关系新增")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActionLinkMappingParam {

    @Schema(description = "行为关联的关系id")
    private String ontologyLinkUniqIdentifier;

    @Schema(description = "行为关联的函数参数表达式")
    private String ontologyLinkFunctionParamExpression;

}
