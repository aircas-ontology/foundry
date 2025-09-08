package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "本体关系graph")
public class OntologyLinkGraphVO {

    @ApiModelProperty(value = "节点，本体列表")
    private List<OntologyMetaVO> nodes;

    @ApiModelProperty(value = "关系，关系列表")
    private List<OntologyLinkCountVO> links;
}
