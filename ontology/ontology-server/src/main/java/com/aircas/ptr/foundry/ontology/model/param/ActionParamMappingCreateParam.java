package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author yangj
 */
@Schema(description = "行为数据参数新增模型")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ActionParamMappingCreateParam {

    @Schema(description = "参数对应的函数参数ID", required = true, example = "fdsfa-gfdsagf-dfs")
    private Long functionParamId;

    @Schema(description = "行为对应的函数参数的表达式", required = true, example = "data.user[0].name")
    private String functionParamExpression;

    @Schema(description = "参数对应的本体属性ID", required = true, example = "fdsfa-gfdsagf-dfs")
    private String propertyUniqueIdentifier;

    @Schema(description = "参数对应的本体", required = true, example = "fdsfa-gfdsagf-dfs")
    private String ontologyUniqueIdentifier;
}
