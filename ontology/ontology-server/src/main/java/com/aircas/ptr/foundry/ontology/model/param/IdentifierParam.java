package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;

@Data
@SuperBuilder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "id Param")
public class IdentifierParam {


    @NotBlank(message = "uniqIdentifier is empty")
    @ApiModelProperty(name = "uniqIdentifier", value = "uniqIdentifier", dataType = "java.lang.String", example = "abcdef")
    private String uniqIdentifier;
}
