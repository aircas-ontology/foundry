package com.aircas.ptr.foundry.ontology.entity.model.param;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@ApiModel(description = "实体主数据源")
public class PrimaryDataSourceParam {

    @ApiModelProperty(name = "tableName",value = "表名", required = true, example = "xtmb")
    private String tableName;

    @ApiModelProperty(name ="primaryKey",value = "主键的列名，必须存在于选择的列参数中", required = true, example = "id")
    private String primaryKey;

    @ApiModelProperty(name="columnParamList", value = "列参数", required = true)
    private List<DataSourceColumnParam> columnParamList;

}
