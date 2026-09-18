package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.ontology.controller.validator.DatasourceVerify;
import com.fasterxml.jackson.databind.JsonNode;
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
@Schema(description = "本体属性创建请求")
public class OntologyPropertyCreateParam extends OntologyIdentifierParam {

    @Schema(name = "datasource", description = "数据源", required = false, example = "xtmb")
    @DatasourceVerify
    private PropertyDatasourceParam datasource;

    @Schema(name = "dataType", description = "数据类型", required = true, example = "Bool")
    @NotNull(message = "dataType is null")
    private OntologyDataTypeEnum dataType;

    @Schema(name = "description", description = "列描述", required = true, example = "名称")
    private String description;

    @Schema(name = "displayName", description = "属性展示名称", example = "飞机", required = true)
    @NotBlank(message = "displayName is empty")
    private String displayName;

    @Schema(name = "apiName", description = "在代码里用的属性名称，格式：^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$", required = true, example = "name")
    @Pattern(regexp = "^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$", message = "列名apiName格式不合法")
    @NotBlank(message = "apiName is empty")
    private String apiName;

    @Schema(name = "isPrimaryKey", description = "是否为主键", required = true, example = "true")
    @NotNull(message = "isPrimaryKey is empty")
    private Boolean isPrimaryKey;

    @Schema(name = "isTitleKey", description = "是否为名称键", required = true, example = "true")
    @NotNull(message = "isTitleKey is empty")
    private Boolean isTitleKey;


    @Schema(name = "type", description = "属性的自定义标签", example = "载荷基本信息")
    private String tag;

    @Schema(name = "defaultValue", description = "属性默认值", example = "123")
    private String defaultValue;

    @Schema(name = "storageGroup", description = "属性存储分组", example = "123")
    @Pattern(regexp = "^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$", message = "存储分组格式不合法")
    @NotBlank(message = "storageGroup is empty")
    private String storageGroup;

    @Schema(name = "categoryId", description = "属性分类id", example = "10")
    private Integer categoryId;

    @Schema(name = "metadata", description = "元数据，json格式")
    private JsonNode metadata;

}
