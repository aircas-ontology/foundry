package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.ontology.controller.validator.DatasourceVerify;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;


@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@ApiModel(description = "本体属性创建请求")
public class OntologyPropertyCreateParam extends OntologyIdentifierParam {

    @ApiModelProperty(name = "datasource", value = "数据源", required = false, example = "xtmb")
    @DatasourceVerify
    private PropertyDatasourceParam datasource;

    @ApiModelProperty(name = "dataType", value = "数据类型", required = true, example = "Bool")
    @NotNull(message = "dataType is null")
    private OntologyDataTypeEnum dataType;

    @ApiModelProperty(name = "description", value = "列描述", required = true, example = "名称")
    @NotBlank(message = "description is empty")
    private String description;

    @ApiModelProperty(name = "displayName", value = "属性展示名称", dataType = "java.lang.String", example = "飞机", required = true)
    @NotBlank(message = "displayName is empty")
    private String displayName;

    @ApiModelProperty(name = "apiName", value = "在代码里用的属性名称，格式：^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$", required = true, example = "name")
    @Pattern(regexp = "^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$", message = "列名apiName格式不合法")
    @NotBlank(message = "apiName is empty")
    private String apiName;

    @ApiModelProperty(name = "isPrimaryKey", value = "是否为主键", required = true, example = "true")
    @NotNull(message = "isPrimaryKey is empty")
    private Boolean isPrimaryKey;

    @ApiModelProperty(name = "isTitleKey", value = "是否为名称键", required = true, example = "true")
    @NotNull(message = "isTitleKey is empty")
    private Boolean isTitleKey;

    @ApiModelProperty(name = "type", value = "属性的自定义标签，默认值：基本属性", example = "载荷基本信息")
    @NotBlank(message = "tag is empty")
    private String tag;
}
