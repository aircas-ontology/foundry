package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "本体统计词条编辑请求")
public class OntologyStatisticLemmaUpdateParam extends OntologyStatisticLemmaCreateParam {


    @Schema(name = "lemmaId", required = true, description = "词条id")
    @NotNull(message = "lemmaId is null")
    private Integer lemmaId;


}
