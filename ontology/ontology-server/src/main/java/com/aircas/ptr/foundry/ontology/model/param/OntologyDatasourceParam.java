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
@ApiModel(description = "OntologyDatasourceParam")
public class OntologyDatasourceParam {

    @ApiModelProperty(name = "tableName",required = true)
    @NotBlank(message = "tableName is empty")
    private String tableName;
}
