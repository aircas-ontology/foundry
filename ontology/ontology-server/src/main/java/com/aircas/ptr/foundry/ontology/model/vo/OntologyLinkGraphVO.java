package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(description = "对象关系图数据（对象-关系-对象）")
public class OntologyLinkGraphVO {

    @Schema(name = "centerUniqueIdentifier", description = "筛选的对象id，为空表示展示全部关系")
    private String centerUniqueIdentifier;

    @Schema(name = "nodes", description = "节点列表（对象）")
    private List<GraphNode> nodes;

    @Schema(name = "edges", description = "边列表（对象-关系-对象）")
    private List<GraphEdge> edges;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(chain = true)
    @Schema(description = "图节点（对象）")
    public static class GraphNode {

        @Schema(name = "uniqueIdentifier", description = "对象uniqueIdentifier")
        private String uniqueIdentifier;

        @Schema(name = "displayName", description = "对象名称")
        private String displayName;

        @Schema(name = "apiName", description = "对象api名称")
        private String apiName;

        @Schema(name = "icon", description = "对象图标")
        private String icon;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(chain = true)
    @Schema(description = "图边（对象-关系-对象）")
    public static class GraphEdge {

        @Schema(name = "uniqueIdentifier", description = "关系uniqueIdentifier")
        private String uniqueIdentifier;

        @Schema(name = "name", description = "关系名称")
        private String name;

        @Schema(name = "from", description = "起始对象uniqueIdentifier")
        private String from;

        @Schema(name = "to", description = "目标对象uniqueIdentifier")
        private String to;

        @Schema(name = "categoryId", description = "关系分类id")
        private Integer categoryId;

        @Schema(name = "apiName", description = "关系api名称")
        private String apiName;

        @Schema(name = "description", description = "关系备注/描述")
        private String description;
    }
}
