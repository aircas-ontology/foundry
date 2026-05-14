package com.aircas.ptr.foundry.ontology.model.dto;


import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyPropertyPrimaryCategoryEnum;
import com.aircas.ptr.foundry.ontology.model.param.OntologyPropertyCreateParam;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
@Accessors(chain = true)
public class OntologyPropertyDTO {

    @ApiModelProperty(name = "dataType", value = "数据类型", required = true, example = "Bool")
    private OntologyDataTypeEnum dataType;

    @ApiModelProperty(name = "description", value = "列描述", required = true, example = "名称")
    private String description;

    @ApiModelProperty(name = "displayName", value = "属性展示名称", dataType = "java.lang.String", example = "飞机", required = true)
    private String displayName;

    @ApiModelProperty(name = "apiName", value = "在代码里用的属性名称，格式：^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$", required = true, example = "name")
    private String apiName;

    @ApiModelProperty(name = "isPrimaryKey", value = "是否为主键", required = true, example = "true")
    private Boolean isPrimaryKey;

    @ApiModelProperty(name = "isTitleKey", value = "是否为名称键", required = true, example = "true")
    private Boolean isTitleKey;

    @ApiModelProperty(name = "primaryCategory", value = "属性一级分类", example = "DESIGN_MANUFACTURING")
    private OntologyPropertyPrimaryCategoryEnum primaryCategory;

    @ApiModelProperty(name = "secondaryCategory", value = "属性二级分类", example = "载荷")
    private String secondaryCategory;

    @ApiModelProperty(name = "type", value = "属性的自定义标签", example = "载荷基本信息")
    private String tag;

    @ApiModelProperty(name = "defaultValue", value = "属性的默认值", example = "30")
    private String defaultValue;
}
