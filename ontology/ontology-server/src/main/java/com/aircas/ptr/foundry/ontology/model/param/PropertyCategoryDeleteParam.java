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
@ApiModel(description = "属性分类体系删除")
public class PropertyCategoryDeleteParam extends OntologyIdentifierParam {


    @ApiModelProperty(name = "categoryId", value = "属性分类id", example = "1")
    @NotNull(message = "categoryId is null")
    private Integer categoryId;

}
