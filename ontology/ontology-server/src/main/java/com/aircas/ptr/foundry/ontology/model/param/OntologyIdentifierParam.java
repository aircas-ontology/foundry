package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@SuperBuilder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "本体id Param")
public class OntologyIdentifierParam {


    @NotBlank(message = "ontologyIdentifier is empty")
    @ApiModelProperty(name = "ontologyIdentifier", value = "本体id", dataType = "java.lang.String", example = "abcdef",required = true)
    private String ontologyIdentifier;
}
