package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Schema(description = "参数")
public class FunctionParameter {

    @Schema(name = "paramName", description = "参数名称")
    private String paramName;

    @Schema(name = "paramValue", description = "参数值")
    private Object paramValue;
}
