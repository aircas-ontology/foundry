package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "本体统计词条创建请求")
public class OntologyStatisticLemmaCreateParam extends OntologyIdentifierParam {


    @Schema(name = "sceneId", description = "统计画布id", required = true)
    @NotBlank(message = "sceneId is empty")
    private String sceneId;

    @Schema(name = "sceneUrl", description = "统计画布url", required = true)
    @NotBlank(message = "sceneUrl is empty")
    private String sceneUrl;

    @Schema(name = "sceneConfig", description = "统计画布配置", required = true)
    @NotBlank(message = "sceneConfig is empty")
    private String sceneConfig;

}
