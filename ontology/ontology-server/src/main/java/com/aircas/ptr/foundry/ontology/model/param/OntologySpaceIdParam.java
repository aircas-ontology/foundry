package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.controller.validator.SpaceIdVerify;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;

@Data
@SuperBuilder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "本体空间id Param")
public class OntologySpaceIdParam {

    @Schema(name = "spaceId", description = "本体空间id", example = "1", required = true)
    @NotNull(message = "spaceId is empty")
    @SpaceIdVerify
    private Integer spaceId;
}
