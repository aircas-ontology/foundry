package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;

@Data
@SuperBuilder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "id Param")
public class IdentifierParam {


    @NotBlank(message = "uniqueIdentifier is empty")
    @Schema(name = "uniqueIdentifier", description = "uniqIdentifier", example = "abcdef")
    private String uniqueIdentifier;
}
