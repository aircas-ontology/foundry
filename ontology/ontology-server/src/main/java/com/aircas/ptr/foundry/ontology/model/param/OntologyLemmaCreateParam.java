package com.aircas.ptr.foundry.ontology.model.param;


import com.aircas.ptr.foundry.common.constant.OntologyLemmaTypeEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
@SuperBuilder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "Ontology Lemma Create Param")
public class OntologyLemmaCreateParam extends OntologyIdentifierParam {

    @ApiModelProperty(name = "title", required = true, value = "标题")
    private String title;

    @ApiModelProperty(name = "content", required = true, value = "内容")
    private String content;

    @ApiModelProperty(name = "parentId", required = false, value = "父词条id,默认为根目录id=0")
    private Integer parentId = 0;

    @ApiModelProperty(name = "type", required = true,  value = "词条类型")
    @NotNull(message = "type is null")
    private OntologyLemmaTypeEnum type;

    @ApiModelProperty(name = "orderIndex", required = false, value = "目录内展示顺序", example = "1")
    @NotNull(message = "orderIndex is null")
    private Integer orderIndex;

    @ApiModelProperty(name = "extraInfo", required = false, value = "其他额外信息")
    private String extraInfo;
}
