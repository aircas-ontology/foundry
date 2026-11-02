package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.common.constant.OntologyDataTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Schema(description = "画布内容查询返回：结构对齐画布一键建空间请求体，前端渲染画布后可直接回传保存")
@Data
@Builder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class OntologySpaceCanvasVO {

    @Schema(name = "spaceId", description = "本体空间id", example = "1")
    private Integer spaceId;

    @Schema(name = "ontologies", description = "空间下的本体对象列表")
    private List<CanvasOntology> ontologies;

    @Schema(name = "links", description = "空间下的对象关系列表")
    private List<CanvasLink> links;

    @Schema(description = "画布本体对象")
    @Data
    @Builder
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CanvasOntology {

        @Schema(name = "displayName", description = "本体名称", example = "舰船aaa")
        private String displayName;

        @Schema(name = "apiName", description = "本体api名称", example = "ship")
        private String apiName;

        @Schema(name = "description", description = "本体描述")
        private String description;

        @Schema(name = "iconUrl", description = "本体图标url")
        private String iconUrl;

        @Schema(name = "properties", description = "本体属性列表")
        private List<CanvasProperty> properties;
    }

    @Schema(description = "画布本体属性")
    @Data
    @Builder
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CanvasProperty {

        @Schema(name = "displayName", description = "属性展示名称", example = "名称")
        private String displayName;

        @Schema(name = "apiName", description = "属性api名称", example = "name")
        private String apiName;

        @Schema(name = "dataType", description = "数据类型（OntologyDataTypeEnum名称）", example = "String")
        private OntologyDataTypeEnum dataType;

        @Schema(name = "storageGroup", description = "存储分组", example = "String")
        private String storageGroup;

        @Schema(name = "description", description = "属性描述")
        private String description;

        @Schema(name = "isPrimaryKey", description = "是否主键")
        private Boolean isPrimaryKey;

        @Schema(name = "isTitleKey", description = "是否名称键")
        private Boolean isTitleKey;

        @Schema(name = "defaultValue", description = "属性默认值")
        private String defaultValue;
    }

    @Schema(description = "画布本体关系")
    @Data
    @Builder
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CanvasLink {

        @Schema(name = "name", description = "关系名称", example = "隶属于")
        private String name;

        @Schema(name = "apiName", description = "关系api名称", example = "lishuyu")
        private String apiName;

        @Schema(name = "description", description = "关系描述")
        private String description;

        @Schema(name = "fromOntologyApiName", description = "源对象api名称", example = "ship")
        private String fromOntologyApiName;

        @Schema(name = "toOntologyApiName", description = "目标对象api名称", example = "country")
        private String toOntologyApiName;
    }
}
