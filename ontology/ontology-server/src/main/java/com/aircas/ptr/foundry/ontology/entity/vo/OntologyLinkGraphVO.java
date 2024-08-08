package com.aircas.ptr.foundry.ontology.entity.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

@Data
@ApiModel(description = "本体关系graph")
public class OntologyLinkGraphVO {

    @ApiModelProperty(value = "节点，本体列表")
    private List<OntologyMetaVO> nodes;

    @ApiModelProperty(value = "关系，关系列表")
    private List<OntologyLinkCountVO> links;
}
