package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.ontology.controller.validator.DatasourceVerify;
import com.aircas.ptr.foundry.ontology.model.enums.OntologyPropertyPrimaryCategoryEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@ApiModel(description = "属性更新请求")
public class OntologyPropertyUpdateParam extends IdentifierParam {

    @ApiModelProperty(name = "datasource", value = "数据源")
    @DatasourceVerify
    private PropertyDatasourceParam datasource;

    @ApiModelProperty(name = "displayName", value = "属性名称", example = "id")
    @NotBlank(message = "displayName is empty")
    private String displayName;


    @ApiModelProperty(name = "dataType", value = "数据类型", required = true, example = "Bool")
    @NotNull(message = "dataType is null")
    private OntologyDataTypeEnum dataType;

    @ApiModelProperty(name = "description", value = "属性描述", example = "id")
    private String description;

    @ApiModelProperty(name = "isTitleKey", value = "是否为名称键", required = true, example = "true")
    @NotNull(message = "isTitleKey is empty")
    private Boolean isTitleKey;

    @ApiModelProperty(name = "isPrimaryKey", value = "是否为主键", required = true, example = "true")
    @NotNull(message = "isPrimaryKey is empty")
    private Boolean isPrimaryKey;


    @ApiModelProperty(name = "primaryCategory", value = "属性一级分类", example = "DESIGN_MANUFACTURING")
    @NotNull(message = "primaryCategory is null")
    private OntologyPropertyPrimaryCategoryEnum primaryCategory;

    @ApiModelProperty(name = "secondaryCategory", value = "属性二级分类", example = "载荷")
    @NotBlank(message = "secondaryCategory is empty")
    private String secondaryCategory;

    @ApiModelProperty(name = "type", value = "属性的自定义标签", example = "载荷基本信息")
    private String tag;

    @ApiModelProperty(name = "defaultValue", value = "属性默认值", example = "123")
    private String defaultValue;

}
