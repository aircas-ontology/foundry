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
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@ApiModel(description = "本体统计词条编辑请求")
public class OntologyStatisticLemmaUpdateParam extends OntologyStatisticLemmaCreateParam {


    @ApiModelProperty(name = "lemmaId", required = true)
    @NotNull(message = "lemmaId is null")
    private Integer lemmaId;


}
