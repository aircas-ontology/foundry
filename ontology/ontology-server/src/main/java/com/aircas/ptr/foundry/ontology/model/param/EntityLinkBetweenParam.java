package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "查询两个实体之间的关联关系")
public class EntityLinkBetweenParam {

    @Schema(name = "ontologyUniqueIdentifierFrom", description = "起点对象id", example = "obj_a", required = true)
    @NotNull(message = "ontologyUniqueIdentifierFrom is null")
    @OntologyIdVerify
    private String ontologyUniqueIdentifierFrom;

    @Schema(name = "entityPrimaryKeyFrom", description = "起点实体主键", example = "1", required = true)
    @NotNull(message = "entityPrimaryKeyFrom is null")
    private Object entityPrimaryKeyFrom;

    @Schema(name = "ontologyUniqueIdentifierTo", description = "终点对象id", example = "obj_b", required = true)
    @NotNull(message = "ontologyUniqueIdentifierTo is null")
    @OntologyIdVerify
    private String ontologyUniqueIdentifierTo;

    @Schema(name = "entityPrimaryKeyTo", description = "终点实体主键", example = "2", required = true)
    @NotNull(message = "entityPrimaryKeyTo is null")
    private Object entityPrimaryKeyTo;
}
