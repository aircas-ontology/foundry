package com.aircas.ptr.foundry.ontology.model.param;


import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@ApiModel(description = "本体行为执行请求")
public class OntologyActionExecuteParam {


    @ApiModelProperty(name = "ontologyUniqueIdentifier", value = "本体id", example = "123")
    @OntologyIdVerify
    private String ontologyUniqueIdentifier;


    @ApiModelProperty(name = "actionApi", value = "actionApi", example = "compute")
    @NotBlank(message = "actionApi is null")
    private String actionApi;


    @ApiModelProperty(name = "shardIndex", value = "shardIndex", example = "1")
    private Integer shardIndex;

    @ApiModelProperty(name = "shardTotal", value = "shardTotal", example = "10")
    private Integer shardTotal;


}
