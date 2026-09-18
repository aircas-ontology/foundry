package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "本体标识")
public class IdentifierVO {


    @Schema(name = "uniqueIdentifier", description = "本体Identifier", example = "abcdef")
    private String uniqueIdentifier;
}
