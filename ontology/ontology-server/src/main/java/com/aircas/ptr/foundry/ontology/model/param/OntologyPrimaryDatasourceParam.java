package com.aircas.ptr.foundry.ontology.model.param;


import com.aircas.ptr.foundry.ontology.controller.validator.PrimaryKeyVerify;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.Valid;
import java.util.List;

@Data
@SuperBuilder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "OntologyDatasourceParam")
public class OntologyPrimaryDatasourceParam {

    @Schema(name = "columnParamList", description = "列参数", required = true)
    @PrimaryKeyVerify
    @Valid
    private List<OntologyDataSourceColumnParam> columnParamList;
}
