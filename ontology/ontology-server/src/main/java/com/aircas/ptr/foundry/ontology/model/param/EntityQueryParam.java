package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "实体查询请求")
public class EntityQueryParam {

    @Schema(name = "ontologyUniqueIdentifier" , description = "本体id", example = "123")
    @OntologyIdVerify
    private String ontologyUniqueIdentifier;

    @Schema(name = "entityPrimaryKey" , description = "实体主键值", example = "123")
    @NotNull(message = "entityPrimaryKey is null")
    private Object entityPrimaryKey;
}
