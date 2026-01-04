package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;

@Data
@SuperBuilder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "Ontology Lemma Update Param")
public class OntologyLemmaUpdateParam extends OntologyLemmaCreateParam {

    @ApiModelProperty(name = "lemmaId", required = true, value = "词条id")
    @NotNull(message = "lemmaId is null")
    private Integer lemmaId;


}
