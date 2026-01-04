package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@ApiModel(description = "本体统计词条创建请求")
public class OntologyStatisticLemmaCreateParam extends OntologyIdentifierParam {


    @ApiModelProperty(name = "sceneId", value = "统计画布id", required = true)
    @NotBlank(message = "sceneId is empty")
    private String sceneId;

    @ApiModelProperty(name = "sceneUrl", value = "统计画布url", required = true)
    @NotBlank(message = "sceneUrl is empty")
    private String sceneUrl;

    @ApiModelProperty(name = "sceneConfig", value = "统计画布配置", required = true)
    @NotBlank(message = "sceneConfig is empty")
    private String sceneConfig;

}
