package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "实体查询请求")
public class EntitySearchParam {

    @ApiModelProperty(name = "ontologyUniqueIdentifier", value = "本体id", example = "123")
    @OntologyIdVerify
    private String ontologyUniqueIdentifier;

    private String propertyName;

    private Object propertyValue;

    @ApiModelProperty(name = "pageNum")
    private Integer pageNum = 1;

    @ApiModelProperty(name = "pageSize")
    private Integer pageSize = 10;


}
