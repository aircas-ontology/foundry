package com.aircas.ptr.foundry.ontology.model.param;


import com.aircas.ptr.foundry.ontology.model.enums.OntologyPropertyCategoryEnum;
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
@ApiModel(description = "OntologyDatasourceParam")
public class OntologyDatasourceParam extends OntologyPrimaryDatasourceParam {

    @ApiModelProperty(name = "category", value = "数据源关联的一组属性的类别，STATIC/DYNAMIC", example = "STATIC")
    @NotNull(message = "category is null")
    private OntologyPropertyCategoryEnum category;

}
