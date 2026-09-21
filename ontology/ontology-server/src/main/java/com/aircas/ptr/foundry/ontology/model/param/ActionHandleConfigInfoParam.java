package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.ActionSchedulingTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Schema(description = "行为执行配置参数")
public class ActionHandleConfigInfoParam extends OntologyIdentifierParam {

    @Schema(name = "actionApi", description = "行为api", required = true, example = "shipLocation")
    @NotBlank(message = "actionApi is empty")
    private String actionApi;

    @Schema(name = "name", description = "行为调度name", required = true, example = "name")
    @NotBlank(message = "name is empty")
    private String name;

    @Schema(name = "description", description = "行为调度描述", required = true, example = "description")
    private String description;

    @Schema(name = "type", description = "行为调度类型", required = true, example = "RULE")
    @NotNull(message = "type is empty")
    private ActionSchedulingTypeEnum type;

}
