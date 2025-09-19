package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@SuperBuilder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "Ontology DataSourceCreate Param")
public class OntologyDataSourceCreateParamOntology extends OntologyIdentifierParam {

    @ApiModelProperty(name = "primaryDataSource", value = "本体对应主数据源", dataType = "OntologyPrimaryDataSourceParam", required = true)
    @NotNull(message = "primaryDataSource is null")
    private OntologyPrimaryDataSourceParam primaryDataSource;

    @ApiModelProperty(name = "associateDataSources", value = "本体关联的其他数据源", dataType = "OntologyAssociateDataSourceParam")
    private List<OntologyAssociateDataSourceParam> associateDataSources;


}
