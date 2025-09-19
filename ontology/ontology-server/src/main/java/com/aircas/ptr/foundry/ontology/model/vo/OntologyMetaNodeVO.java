package com.aircas.ptr.foundry.ontology.model.vo;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@Builder
@Accessors(chain = true)
@ApiModel(value = "本体节点信息")
public class OntologyMetaNodeVO {

    /**
     * 唯一标识
     */
    @ApiModelProperty(name = "uniqueIdentifier")
    private String uniqueIdentifier;

    /**
     * 本体名称
     */
    @ApiModelProperty(name = "displayName", value = "本体名称", example = "飞机")
    private String displayName;


    /**
     * 子本体
     */
    @ApiModelProperty(name = "childNodes")
    private List<OntologyMetaNodeVO> childNodes;

}
