package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.OntologyLemmaTypeEnum;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "本体词条详情")
public class OntologyLemmaVO {

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
    private JsonNode extraInfo;
}
