package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@ApiModel(description = "属性更新请求")
public class OntologyPropertyUpdateParam extends IdentifierParam {

    @ApiModelProperty(name = "displayName", value = "属性名称", example = "id")
    private String displayName;

    @ApiModelProperty(name = "description", value = "属性描述", example = "id")
    private String description;

    @ApiModelProperty(name = "isTitleKey", value = "是否为名称键", required = true, example = "true")
    private Boolean isTitleKey;

    @ApiModelProperty(name = "isAssociateKey", value = "是否为与主数据源关联的列名", example = "false")
    private Boolean isAssociateKey;

    @ApiModelProperty(name = "associateDatasourceColumnName", value = "关联的主数据源表的列名，必须存在于主数据源表的列中", example = "id")
    private String associateDatasourceColumnName;

    @ApiModelProperty(name = "type", value = "属性的自定义标签，默认值：基本属性", example = "载荷基本信息")
    private String tag = "基本属性";

}
