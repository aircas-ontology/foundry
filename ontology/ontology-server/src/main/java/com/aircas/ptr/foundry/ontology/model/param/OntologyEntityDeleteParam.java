package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.annotations.Api;
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
@ApiModel(description = "实体删除请求")
public class OntologyEntityDeleteParam extends OntologyIdentifierParam{

    @ApiModelProperty(name = "entityPrimaryKey",value = "实体主键")
    @NotBlank(message = "entityPrimaryKey is empty")
    private String entityPrimaryKey;
}
