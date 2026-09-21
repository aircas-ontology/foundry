package com.aircas.ptr.foundry.ontology.model.param;


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
@Schema(description = "实体行为执行请求")
public class EntityActionExecuteParam extends EntityQueryParam {


    @Schema(name = "actionApi", description = "actionApi", example = "compute")
    @NotBlank(message = "actionApi is null")
    private String actionApi;


    @Schema(name = "linkedOntologyUniqueIdentifier", description = "关联本体id", example = "123")
    private String linkedOntologyUniqueIdentifier;

    @Schema(name = "linkedEntityPrimaryKey", description = "关联本体id下某实体主键值", example = "123")
    private Object linkedEntityPrimaryKey;
}
