package com.aircas.ptr.foundry.ontology.entity.model.param;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@ApiModel(description = "实体关联的其他数据源")
public class AssociateDataSourceParam extends PrimaryDataSourceParam {

    @ApiModelProperty(name = "associateKey", value = "tableName中与主数据源关联的列名，必须存在于选择的列参数中", required = true, example = "id")
    private String associateKey;

    @ApiModelProperty(name = "primaryDataSourceKey", value = "关联的主数据源表的列名，必须存在于主数据源表的列中", required = true, example = "id")
    private String primaryDataSourceKey;

}
