package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Accessors(chain = true)
@ApiModel(description = "本体关系graph")
public class OntologyGroupLinkVO {


    /**
     * 本体的unique identifier
     */
    @ApiModelProperty(name = "ontologyUniqueIdentifier",value = "本体唯一标识")
    private String ontologyUniqueIdentifier;

    /**
     * 本体的name
     */
    @ApiModelProperty(name = "name",value = "本体名称")
    private String name;


    @ApiModelProperty(name = "links", value = "关系，关系列表")
    private List<OntologyLinkInfoVO> links;
}
