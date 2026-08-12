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
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@ApiModel(description = "本体分类VO")
public class OntologyCategoryVO {

    @ApiModelProperty(name = "categoryId", value = "分类id", example = "1")
    private Integer categoryId;

    @ApiModelProperty(name = "name", value = "分类名称", example = "平台")
    private String name;

    @ApiModelProperty(name = "ontologyMetaInfos", value = "本体信息")
    private List<OntologyMetaInfoVO> ontologyMetaInfos;

    @ApiModelProperty(name = "children", value = "子分类")
    private List<OntologyCategoryVO> children;
}
