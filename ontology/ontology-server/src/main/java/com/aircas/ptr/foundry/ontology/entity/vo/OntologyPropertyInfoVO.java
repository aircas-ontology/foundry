package com.aircas.ptr.foundry.ontology.entity.vo;

import com.aircas.ptr.foundry.model.po.OntologyDataType;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel(description = "本体属性基本信息")
public class OntologyPropertyInfoVO {

    @ApiModelProperty(name = "propertyType", value = "属性基础类型、时间、字符、数值", example = "String")
    private OntologyDataType propertyType;

    @ApiModelProperty(name = "displayName", value = "属性名称", example = "名称")
    private String displayName;

    @ApiModelProperty(name = "apiName", value = "在代码里用的属性名称、驼峰式", example = "mbbh")
    private String apiName;
}
