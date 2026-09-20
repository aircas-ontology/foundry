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
@Schema(description = "本体关系图数据（本体-关系-本体）")
public class OntologyLinkGraphVO {

    @Schema(name = "centerUniqueIdentifier", description = "筛选的本体对象id，为空表示展示全部关系")
    private String centerUniqueIdentifier;

    @Schema(name = "nodes", description = "节点列表（本体对象）")
    private List<GraphNode> nodes;

    @Schema(name = "edges", description = "边列表（本体-关系-本体）")
    private List<GraphEdge> edges;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(chain = true)
    @Schema(description = "图节点（本体对象）")
    public static class GraphNode {

        @Schema(name = "uniqueIdentifier", description = "本体uniqueIdentifier")
        private String uniqueIdentifier;

        @Schema(name = "displayName", description = "本体名称")
        private String displayName;

        @Schema(name = "apiName", description = "本体api名称")
        private String apiName;

        @Schema(name = "icon", description = "本体图标")
        private String icon;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Accessors(chain = true)
    @Schema(description = "图边（本体-关系-本体）")
    public static class GraphEdge {

        @Schema(name = "uniqueIdentifier", description = "关系uniqueIdentifier")
        private String uniqueIdentifier;

        @Schema(name = "name", description = "关系名称")
        private String name;

        @Schema(name = "from", description = "起始本体uniqueIdentifier")
        private String from;

        @Schema(name = "to", description = "目标本体uniqueIdentifier")
        private String to;

        @Schema(name = "categoryId", description = "关系分类id")
        private Integer categoryId;
    }
}
