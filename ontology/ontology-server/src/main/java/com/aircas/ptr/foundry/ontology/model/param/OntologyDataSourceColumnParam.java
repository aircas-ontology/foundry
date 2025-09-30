package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.common.constant.PostgresDataTypeEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.Valid;
import javax.validation.constraints.Pattern;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@ApiModel(description = "ontology datasource column param")
public class OntologyDataSourceColumnParam {

    @ApiModelProperty(name = "datasourceColumnName", value = "数据源列名", required = true, example = "id")
    private String datasourceColumnName;

    @ApiModelProperty(name = "datasourceColumnType", value = "数据源列类型，仅只支持postgresql", required = true, example = "bigint")
    private PostgresDataTypeEnum datasourceColumnType;

    @ApiModelProperty(name = "description", value = "列描述", required = true, example = "名称")
    private String description;

    @ApiModelProperty(name = "displayName", value = "属性展示名称", dataType = "java.lang.String", example = "飞机", required = true)
    private String displayName;

    @ApiModelProperty(name = "apiName", value = "在代码里用的属性名称，格式：^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$", required = true, example = "name")
    @Pattern(regexp = "^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$", message = "列名apiName格式不合法")
    private String apiName;

    @ApiModelProperty(name = "datasourceId", value = "数据源表名", required = true, example = "xtmb")
    private String datasourceId;

    @ApiModelProperty(name = "isPrimaryKey", value = "是否为主键", required = true, example = "true")
    private Boolean isPrimaryKey;

    @ApiModelProperty(name = "isTitleKey", value = "是否为名称键", required = true, example = "true")
    private Boolean isTitleKey;

    @ApiModelProperty(name = "isAssociateKey", value = "是否为与主数据源关联的列名", example = "false")
    private Boolean isAssociateKey;

    @ApiModelProperty(name = "associateDatasourceColumnName", value = "关联的主数据源表的列名，必须存在于主数据源表的列中", example = "id")
    private String associateDatasourceColumnName;
}
