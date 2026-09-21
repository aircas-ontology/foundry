package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.common.constant.PostgresDataTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "ontology datasource column param")
public class OntologyDataSourceColumnParam {

    @Schema(name = "datasourceColumnName", description = "数据源列名", required = true, example = "id")
    @NotBlank(message = "datasourceColumnName is empty")
    private String datasourceColumnName;

    @Schema(name = "datasourceColumnType", description = "数据源列类型，仅只支持postgresql", required = true, example = "bigint")
    @NotNull(message = "datasourceColumnType is null")
    private PostgresDataTypeEnum datasourceColumnType;

    @Schema(name = "description", description = "列描述", required = true, example = "名称")
    @NotBlank(message = "description is empty")
    private String description;

    @Schema(name = "displayName", description = "属性展示名称", example = "飞机", required = true)
    @NotBlank(message = "displayName is empty")
    private String displayName;

    @Schema(name = "apiName", description = "在代码里用的属性名称，格式：^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$", required = true, example = "name")
    @Pattern(regexp = "^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$", message = "列名apiName格式不合法")
    @NotBlank(message = "apiName is empty")
    private String apiName;

    @Schema(name = "datasourceId", description = "数据源表名", required = true, example = "xtmb")
    @NotBlank(message = "datasourceId is empty")
    private String datasourceId;

    @Schema(name = "isPrimaryKey", description = "是否为主键", required = true, example = "true")
    private Boolean isPrimaryKey = false;

    @Schema(name = "isTitleKey", description = "是否为名称键", required = true, example = "true")
    private Boolean isTitleKey = false;

    @Schema(name = "isAssociateKey", description = "是否为与主数据源关联的列名", example = "false")
    private Boolean isAssociateKey = false;

    @Schema(name = "associateDatasourceColumnName", description = "关联的主数据源表的列名，必须存在于主数据源表的列中", example = "id")
    private String associateDatasourceColumnName;

    @Schema(name = "type", description = "属性的自定义标签", example = "载荷基本信息")
    private String tag;

}
