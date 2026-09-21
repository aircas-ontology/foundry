package com.aircas.ptr.foundry.ontology.model.param;


import com.aircas.ptr.foundry.ontology.model.enums.OntologyLemmaTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotNull;

@Data
@SuperBuilder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Ontology Lemma Create Param")
public class OntologyLemmaCreateParam extends OntologyIdentifierParam {

    @Schema(name = "title", required = true, description = "标题")
    private String title;

    @Schema(name = "content", required = true, description = "内容")
    private String content;

    @Schema(name = "parentId", required = false, description = "父词条id,默认为根目录id=0")
    private Integer parentId = 0;

    @Schema(name = "type", required = true,  description = "词条类型")
    @NotNull(message = "type is null")
    private OntologyLemmaTypeEnum type;

    @Schema(name = "orderIndex", required = false, description = "目录内展示顺序", example = "1")
    @NotNull(message = "orderIndex is null")
    private Integer orderIndex;

    @Schema(name = "extraInfo", required = false, description = "其他额外信息")
    private String extraInfo;
}
