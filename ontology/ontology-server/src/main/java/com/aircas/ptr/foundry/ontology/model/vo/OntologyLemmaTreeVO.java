package com.aircas.ptr.foundry.ontology.model.vo;


import com.aircas.ptr.foundry.ontology.model.enums.OntologyLemmaTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Data
@Accessors(chain = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "本体词条树")
public class OntologyLemmaTreeVO implements Comparable<OntologyLemmaTreeVO> {

    @Schema(name = "ontologyUniqueIdentifier", required = true, description = "本体")
    private String ontologyUniqueIdentifier;

    @Schema(name = "lemmaId", required = true, description = "词条id")
    private Integer lemmaId;

    @Schema(name = "title", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(name = "content", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    @Schema(name = "parentId", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer parentId;

    @Schema(name = "type", requiredMode = Schema.RequiredMode.REQUIRED)
    private OntologyLemmaTypeEnum type;

    @Schema(name = "orderIndex", required = false, description = "展示顺序", example = "1")
    private Integer orderIndex;

    @Schema(name = "extraInfo")
    private String extraInfo;

    @Schema(name = "child", required = false, description = "子词条")
    private List<OntologyLemmaTreeVO> child;

    @Override
    public int compareTo(@NotNull OntologyLemmaTreeVO o) {
        return this.orderIndex - o.orderIndex;
    }
}
