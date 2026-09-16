package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
@SuperBuilder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "本体id Param")
public class OntologyIdentifierParam {

    @NotBlank(message = "ontologyIdentifier is empty")
    @OntologyIdVerify
    @Schema(name = "ontologyIdentifier", description = "本体id", example = "abcdef",required = true)
    private String ontologyIdentifier;
}
