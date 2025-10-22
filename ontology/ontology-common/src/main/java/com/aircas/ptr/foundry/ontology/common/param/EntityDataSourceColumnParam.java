package com.aircas.ptr.foundry.ontology.common.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Accessors(chain = true)
@ApiModel(description = "ontology datasource column param")
public class EntityDataSourceColumnParam {

    @ApiModelProperty(name = "columnName", value = "列名", required = true, example = "id")
    private String columnName;

    @ApiModelProperty(name = "columnType", value = "列类型", required = true, example = "bigint")
    private String columnType;

    @ApiModelProperty(name = "description", value = "列描述", required = true, example = "名称")
    private String description;

    @ApiModelProperty(name = "tableName",value = "表名", required = true, example = "xtmb")
    private String tableName;

    @ApiModelProperty(name = "datasourceId",value = "数据源表名", required = true, example = "xtmb")
    private String datasourceId;

    @ApiModelProperty(name = "datasourceColumnName",value = "数据源列名", required = true, example = "name")
    private String datasourceColumnName;

    @ApiModelProperty(name ="primaryKey",value = "主键的列名，必须存在于选择的列参数中", required = true, example = "id")
    private Boolean isPrimaryKey;

    @ApiModelProperty(name ="isTitleKey",value = "名称健", required = true, example = "name")
    private Boolean isTitleKey;

    @ApiModelProperty(name = "isAssociateKey", value = "是否为与主数据源关联的列名，必须存在于选择的列参数中", required = true, example = "id")
    private Boolean isAssociateKey;

    @ApiModelProperty(name = "associateDatasourceColumnName", value = "关联的主数据源表的列名，必须存在于主数据源表的列中", required = true, example = "id")
    private String associateDatasourceColumnName;
}
