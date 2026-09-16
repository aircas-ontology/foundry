package com.aircas.ptr.foundry.ontology.model.param;


import com.aircas.ptr.foundry.ontology.model.enums.OntologyPropertyPrimaryCategoryEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;

@Data
@SuperBuilder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "OntologyDatasourceParam")
public class OntologyDatasourceParam extends OntologyPrimaryDatasourceParam {

    @Schema(name = "category", description = "数据源关联的一组属性的类别，STATIC/DYNAMIC", example = "STATIC")
    @NotNull(message = "category is null")
    private OntologyPropertyPrimaryCategoryEnum category;

}
