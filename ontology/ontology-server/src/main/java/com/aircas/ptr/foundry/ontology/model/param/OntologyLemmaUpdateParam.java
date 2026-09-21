package com.aircas.ptr.foundry.ontology.model.param;


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
@Schema(description = "Ontology Lemma Update Param")
public class OntologyLemmaUpdateParam extends OntologyLemmaCreateParam {

    @Schema(name = "lemmaId", required = true, description = "词条id")
    @NotNull(message = "lemmaId is null")
    private Integer lemmaId;


}
