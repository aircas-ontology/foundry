package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Schema(description = "行为调度编辑请求")
public class ActionSchedulingUpdateParam extends ActionSchedulingCreateParam {

    @Schema(name = "id", description = "行为调度id", required = true, example = "123")
    @NotNull(message = "id is null")
    private Long id;

}
