package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.OntologyDataTypeEnum;
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

    @ApiModelProperty(name = "datasourceColumnName", value = "数据源列名", example = "id")
    private String datasourceColumnName;

    @ApiModelProperty(name = "datasourceId", value = "数据源表名", example = "xtmb")
    private String datasourceId;

    @ApiModelProperty(name = "datasourceId", value = "数据源表名描述", example = "xtmb")
    private String datasourceDescription;

    @ApiModelProperty(name = "propertyType", value = "属性基础类型、时间、字符、数值", example = "String")
    private OntologyDataTypeEnum propertyType;

    @ApiModelProperty(name = "apiName", value = "在代码里用的属性名称、驼峰式", example = "mbbh")
    private String apiName;

}
