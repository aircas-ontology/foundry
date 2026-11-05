package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Schema(description = "画布内容（对象、属性、关系）：POST /space/canvas 保存请求体与 GET /space/canvas 查询返回体共用此结构")
@Data
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class OntologySpaceCanvasCreateParam {

    @Schema(name = "spaceId", description = "对象空间id", required = true, example = "1")
    private Integer spaceId;

    @Schema(name = "base", description = "加载画布时 GET /space/canvas 返回的原始快照（ontologies、links 原样回传），"
            + "保存时用于和数据库做三段对比；不传表示纯新增模式，此时所有项都不能带 uniqueIdentifier")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private CanvasSnapshot base;

    @Schema(name = "ontologies", description = "画布中的对象列表（编辑后的完整列表，删除对象即从列表中移除）")
    @Valid
    private List<CanvasOntology> ontologies;

    @Schema(name = "links", description = "画布中的关系列表（编辑后的完整列表，删除关系即从列表中移除）")
    @Valid
    private List<CanvasLink> links;

    @Schema(description = "画布对象")
    @Data
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CanvasOntology {

        @Schema(name = "uniqueIdentifier", description = "对象唯一标识：已有对象回传 GET 返回的原值，新增对象不传", example = "8039c5f9-5579-4ee4-ba94-b2f25f785dd6")
        private String uniqueIdentifier;

        @Schema(name = "displayName", description = "对象名称", required = true, example = "舰船aaa")
        @NotBlank(message = "ontology displayName is empty")
        private String displayName;

        @Schema(name = "apiName", description = "对象api名称", required = true, example = "ship")
        @NotBlank(message = "ontology apiName is empty")
        @Pattern(regexp = "^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$", message = "对象apiName格式不合法")
        private String apiName;

        @Schema(name = "description", description = "对象描述")
        private String description;

        @Schema(name = "iconUrl", description = "对象图标url")
        private String iconUrl;

        @Schema(name = "properties", description = "对象属性列表")
        @Valid
        private List<CanvasProperty> properties;
    }

    @Schema(description = "画布对象属性")
    @Data
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CanvasProperty {

        @Schema(name = "uniqueIdentifier", description = "属性唯一标识：已有属性回传 GET 返回的原值，新增属性不传", example = "6f1c2b7a-3d4e-4f5a-9b8c-0d1e2f3a4b5c")
        private String uniqueIdentifier;

        @Schema(name = "displayName", description = "属性展示名称", required = true, example = "名称")
        @NotBlank(message = "property displayName is empty")
        private String displayName;

        @Schema(name = "apiName", description = "属性api名称", required = true, example = "name")
        @NotBlank(message = "property apiName is empty")
        @Pattern(regexp = "^[a-zA-Z_$][a-zA-Z0-9_$]{0,62}$", message = "属性apiName格式不合法")
        private String apiName;

        @Schema(name = "dataType", description = "数据类型（OntologyDataTypeEnum名称），不传默认String", example = "String")
        private OntologyDataTypeEnum dataType;

        @Schema(name = "storageGroup", description = "存储分组", example = "String")
        private String  storageGroup;

        @Schema(name = "description", description = "属性描述")
        private String description;

        @Schema(name = "isPrimaryKey", description = "是否主键，不传默认false")
        private Boolean isPrimaryKey;

        @Schema(name = "isTitleKey", description = "是否名称键，不传默认false")
        private Boolean isTitleKey;

        @Schema(name = "defaultValue", description = "属性默认值")
        private String defaultValue;
    }

    @Schema(description = "画布对象关系")
    @Data
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CanvasLink {

        @Schema(name = "uniqueIdentifier", description = "关系唯一标识：已有关系回传 GET 返回的原值，新增关系不传", example = "5a4b3c2d-1e0f-4a9b-8c7d-6e5f4a3b2c1d")
        private String uniqueIdentifier;

        @Schema(name = "name", description = "关系名称", required = true, example = "隶属于")
        @NotBlank(message = "link name is empty")
        private String name;

        @Schema(name = "apiName", description = "关系api名称", example = "lishuyu")
        private String apiName;

        @Schema(name = "description", description = "关系描述")
        private String description;

        @Schema(name = "fromOntologyApiName", description = "源对象api名称（也兼容传对象显示名称）", required = true, example = "ship")
        @NotBlank(message = "fromOntologyApiName is empty")
        private String fromOntologyApiName;

        @Schema(name = "toOntologyApiName", description = "目标对象api名称（也兼容传对象显示名称）", required = true, example = "country")
        @NotBlank(message = "toOntologyApiName is empty")
        private String toOntologyApiName;
    }

    @Schema(description = "画布加载时的原始快照，字段结构与 GET /space/canvas 返回的 ontologies、links 完全一致")
    @Data
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CanvasSnapshot {

        @Schema(name = "ontologies", description = "加载时的对象列表（原样回传，不要修改）")
        private List<CanvasOntology> ontologies;

        @Schema(name = "links", description = "加载时的关系列表（原样回传，不要修改）")
        private List<CanvasLink> links;
    }
}
