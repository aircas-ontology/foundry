package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
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
@ApiModel(description = "本体属性基本信息")
public class OntologyPropertyInfoVO {

    @ApiModelProperty(name = "propertyType", value = "属性基础类型、时间、字符、数值", example = "String")
    private OntologyDataTypeEnum propertyType;

    @ApiModelProperty(name = "displayName", value = "属性名称", example = "名称")
    private String displayName;

    @ApiModelProperty(name = "description", value = "属性描述", example = "描述")
    private String description;

    @ApiModelProperty(name = "apiName", value = "在代码里用的属性名称、驼峰式", example = "mbbh")
    private String apiName;

    /**
     * 是否为主键，1是0否
     */
    @ApiModelProperty(name = "isPrimaryKey", value = "是否为主键")
    private Boolean isPrimaryKey;

    /**
     * 是否为名称键，将该属性作为本体的显示名称，1是0否
     */
    @ApiModelProperty(name = "isTitleKey", value = "是否为名称键")
    private Boolean isTitleKey;

    /**
     * 唯一标示
     */
    @ApiModelProperty(name = "uniqueIdentifier", value = "id")
    private String uniqueIdentifier;


}
