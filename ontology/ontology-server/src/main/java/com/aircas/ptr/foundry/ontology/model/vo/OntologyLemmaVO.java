package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.common.constant.OntologyLemmaTypeEnum;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "本体词条详情")
public class OntologyLemmaVO {

    @ApiModelProperty(name = "ontologyUniqueIdentifier", required = true, value = "本体")
    private String ontologyUniqueIdentifier;

    @ApiModelProperty(name = "lemmaId", required = true, value = "词条id")
    private Integer lemmaId;

    @ApiModelProperty(name = "title", required = true)
    private String title;

    @ApiModelProperty(name = "content", required = true)
    private String content;

    @ApiModelProperty(name = "parentId", required = true)
    private Integer parentId;

    @ApiModelProperty(name = "type", required = true)
    private OntologyLemmaTypeEnum type;

    @ApiModelProperty(name = "orderIndex", required = false, value = "展示顺序", example = "1")
    private Integer orderIndex;

    @ApiModelProperty(name = "extraInfo", required = false)
    private JsonNode extraInfo;
}
