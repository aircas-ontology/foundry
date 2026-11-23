package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.aircas.ptr.foundry.ontology.controller.validator.DatasourceVerify;
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
@Schema(description = "对象属性创建请求")
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

    @Schema(name = "metadata", description = "属性元数据，约定为JSON对象字符串：键=schema节点名称从根逐层嵌套，叶子值=字符串（ENUM取单个候选值，非数组）；"
            + "也兼容直接传JSON对象（服务端转为字符串）；解析不成JSON对象的字符串原样存储。旧格式selectedRecords不再支持，提交会报「元数据字段不在schema中」。"
            + "能解析为对象时须符合该对象已定义的元数据schema（键存在性校验），对象未定义schema时拒绝。"
            + "示例：{\"元数据\":{\"枚举类\":{\"是否必填\":\"是\"},\"范围类\":{\"取值范围\":\"500\"}}}",
            example = "{\"元数据\":{\"枚举类\":{\"是否必填\":\"是\"},\"范围类\":{\"取值范围\":\"500\"}}}")
    private Object metadata;

}
