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
@ApiModel(description = "分类更新请求")
public class OntologyCategoryUpdateParam extends OntologyCategoryDeleteParam {

    @ApiModelProperty(name = "name", value = "属性分类名称", example = "平台")
    @NotBlank(message = "name is empty")
    private String name;
}
