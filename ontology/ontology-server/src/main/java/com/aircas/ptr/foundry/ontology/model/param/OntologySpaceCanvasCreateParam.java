package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Schema(description = "画布一键建空间请求：创建空间并批量创建对象、属性、关系")
@Data
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class OntologySpaceCanvasCreateParam {

    @Schema(name = "iconUrl", description = "空间图标url")
    private String iconUrl;

    @Schema(name = "displayName", description = "空间名称", required = true, example = "公共安全")
    @NotBlank(message = "displayName is empty")
    private String displayName;

    @Schema(name = "description", description = "空间描述")
    private String description;

    @Schema(name = "apiName", description = "空间api名称", required = true, example = "public_security")
    @NotBlank(message = "apiName is empty")
    @Pattern(regexp = "^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$", message = "空间api名称格式不合法")
    private String apiName;

    @Schema(name = "ontologies", description = "画布中的本体对象列表")
    @Valid
    private List<CanvasOntology> ontologies;

    @Schema(name = "links", description = "画布中的关系列表")
    @Valid
    private List<CanvasLink> links;

    @Schema(description = "画布本体对象")
    @Data
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CanvasOntology {

        @Schema(name = "displayName", description = "本体名称", required = true, example = "舰船aaa")
        @NotBlank(message = "ontology displayName is empty")
        private String displayName;

        @Schema(name = "apiName", description = "本体api名称", required = true, example = "ship")
        @NotBlank(message = "ontology apiName is empty")
        @Pattern(regexp = "^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$", message = "本体apiName格式不合法")
        private String apiName;

        @Schema(name = "description", description = "本体描述")
        private String description;

        @Schema(name = "iconUrl", description = "本体图标url")
        private String iconUrl;

        @Schema(name = "properties", description = "本体属性列表")
        @Valid
        private List<CanvasProperty> properties;
    }

    @Schema(description = "画布本体属性")
    @Data
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CanvasProperty {

        @Schema(name = "displayName", description = "属性展示名称", required = true, example = "名称")
        @NotBlank(message = "property displayName is empty")
        private String displayName;

        @Schema(name = "apiName", description = "属性api名称", required = true, example = "name")
        @NotBlank(message = "property apiName is empty")
        @Pattern(regexp = "^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$", message = "属性apiName格式不合法")
        private String apiName;

        @Schema(name = "dataType", description = "数据类型（OntologyDataTypeEnum名称），不传默认String", example = "String")
        private OntologyDataTypeEnum dataType;

        @Schema(name = "description", description = "属性描述")
        private String description;

        @Schema(name = "isPrimaryKey", description = "是否主键，不传默认false")
        private Boolean isPrimaryKey;

        @Schema(name = "isTitleKey", description = "是否名称键，不传默认false")
        private Boolean isTitleKey;

        @Schema(name = "defaultValue", description = "属性默认值")
        private String defaultValue;
    }

    @Schema(description = "画布本体关系")
    @Data
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CanvasLink {

        @Schema(name = "name", description = "关系名称", required = true, example = "隶属于")
        @NotBlank(message = "link name is empty")
        private String name;

        @Schema(name = "apiName", description = "关系api名称（预留字段，当前不落库）", example = "lishuyu")
        private String apiName;

        @Schema(name = "description", description = "关系描述（预留字段，当前不落库）")
        private String description;

        @Schema(name = "fromOntologyApiName", description = "源对象api名称（也兼容传对象显示名称）", required = true, example = "ship")
        @NotBlank(message = "fromOntologyApiName is empty")
        private String fromOntologyApiName;

        @Schema(name = "toOntologyApiName", description = "目标对象api名称（也兼容传对象显示名称）", required = true, example = "country")
        @NotBlank(message = "toOntologyApiName is empty")
        private String toOntologyApiName;
    }
}
