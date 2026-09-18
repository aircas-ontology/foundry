package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Schema(description = "画布一键建空间返回")
@Data
@Builder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class OntologySpaceCanvasCreateVO {

    @Schema(name = "spaceId", description = "新建空间id")
    private Integer spaceId;

    @Schema(name = "ontologies", description = "创建出的本体列表（含apiName与uniqueIdentifier映射）")
    private List<OntologyItem> ontologies;

    @Schema(description = "本体创建结果项")
    @Data
    @Builder
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OntologyItem {

        @Schema(name = "apiName", description = "本体api名称")
        private String apiName;

        @Schema(name = "displayName", description = "本体名称")
        private String displayName;

        @Schema(name = "uniqueIdentifier", description = "本体uniqueIdentifier")
        private String uniqueIdentifier;
    }
}
