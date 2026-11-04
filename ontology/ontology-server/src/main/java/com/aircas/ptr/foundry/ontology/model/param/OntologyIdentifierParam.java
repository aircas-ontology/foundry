package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
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
@Schema(description = "对象 id Param")
public class OntologyIdentifierParam {

    @OntologyIdVerify
    @Schema(name = "ontologyIdentifier", description = "对象 id（行为挂载的对象；应用行为可不传，从 linkMapping 推导）", example = "abcdef")
    private String ontologyIdentifier;
}
