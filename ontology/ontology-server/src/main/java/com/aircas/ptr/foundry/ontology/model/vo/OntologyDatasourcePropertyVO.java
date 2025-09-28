package com.aircas.ptr.foundry.ontology.model.vo;

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
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@ApiModel(description = "本体数据源属性详细信息")
public class OntologyDatasourcePropertyVO {

    @ApiModelProperty(name = "primaryDataSource", value = "本体对应主数据源属性列表", dataType = "OntologyPrimaryDataSourceParam", required = true)
    @NotNull(message = "primaryDataSource is null")
    private List<OntologyPropertyDetailVO> primaryDataSource;

    @ApiModelProperty(name = "associateDataSources", value = "本体关联的其他数据源属性", dataType = "OntologyAssociateDataSourceParam")
    private List<List<OntologyPropertyDetailVO>> associateDataSources;
}
