package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "本体标识")
public class IdentifierVO {


    @ApiModelProperty(name = "uniqueIdentifier", value = "本体Identifier", dataType = "java.lang.String", example = "abcdef")
    private String uniqueIdentifier;
}
