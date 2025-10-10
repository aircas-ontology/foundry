package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.common.constant.OntologyPropertyCategoryEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@ApiModel(description = "本体属性详细信息")
public class OntologyPropertyDetailVO extends OntologyPropertyInfoVO {

    @ApiModelProperty(name = "isAssociateKey", value = "是否为与主数据源关联的列名", example = "false")
    private Boolean isAssociateKey;

    @ApiModelProperty(name = "associateDatasourceColumnName", value = "关联的主数据源表的列名，必须存在于主数据源表的列中", example = "id")
    private String associateDatasourceColumnName;

    @ApiModelProperty(name = "datasourceColumnName", value = "数据源列名", example = "id")
    private String datasourceColumnName;

    @ApiModelProperty(name = "datasourceId", value = "数据源表名", example = "xtmb")
    private String datasourceId;

    @ApiModelProperty(name = "tag", value = "属性标签")
    private String tag;

    @ApiModelProperty(name = "type", value = "属性类别")
    private OntologyPropertyCategoryEnum category;
}
