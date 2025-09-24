package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.common.constant.PostgresDataTypeEnum;
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
@ApiModel(description = "ontology datasource column param")
public class OntologyDataSourceColumnParam {

    @ApiModelProperty(name = "columnName",value = "列名", required = true, example = "id")
    private String columnName;

    @ApiModelProperty(name="columnType", value = "列类型，仅只支持postgresql", required = true, example = "bigint")
    private PostgresDataTypeEnum columnType;

    @ApiModelProperty(name="description", value = "列描述", required = true, example = "名称")
    private String description;

    @ApiModelProperty(name = "displayName", value = "属性展示名称", dataType = "java.lang.String", example = "飞机", required = true)
    private String displayName;

    @ApiModelProperty(name="apiName",value = "在代码里用的属性名称", required = true, example = "name")
    private String apiName;

    @ApiModelProperty(name = "tableName", value = "表名", required = true, example = "xtmb")
    private String tableName;

    @ApiModelProperty(name ="isPrimaryKey",value = "是否为主键", required = true, example = "true")
    private Boolean isPrimaryKey;

    @ApiModelProperty(name ="isTitleKey",value = "是否为名称键", required = true, example = "true")
    private Boolean isTitleKey;

    @ApiModelProperty(value = "是否为与主数据源关联的列名", example = "false")
    private Boolean isAssociateKey;

    @ApiModelProperty(value = "关联的主数据源表的列名，必须存在于主数据源表的列中", example = "id")
    private String primaryDataSourceKey;


}
