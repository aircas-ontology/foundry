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
@SuperBuilder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "分类删除请求")
public class OntologyCategoryDeleteParam extends OntologySpaceIdParam {

    @ApiModelProperty(name = "categoryId", value = "分类id", example = "1")
    @NotNull(message = "categoryId is null")
    private Integer categoryId;
}
