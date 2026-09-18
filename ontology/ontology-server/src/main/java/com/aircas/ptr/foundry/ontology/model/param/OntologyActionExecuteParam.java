package com.aircas.ptr.foundry.ontology.model.param;


import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "本体行为执行请求")
public class OntologyActionExecuteParam {


    @Schema(name = "ontologyUniqueIdentifier", description = "本体id", example = "123")
    @OntologyIdVerify
    private String ontologyUniqueIdentifier;


    @Schema(name = "actionApi", description = "actionApi", example = "compute")
    @NotBlank(message = "actionApi is null")
    private String actionApi;


    @Schema(name = "shardIndex", description = "shardIndex", example = "1")
    private Integer shardIndex;

    @Schema(name = "shardTotal", description = "shardTotal", example = "10")
    private Integer shardTotal;


}
