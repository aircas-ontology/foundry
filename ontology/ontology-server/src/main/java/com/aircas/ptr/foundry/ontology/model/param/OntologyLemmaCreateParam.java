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

    @ApiModelProperty(name = "title", required = true)
    @NotBlank(message = "title is empty")
    private String title;

    @ApiModelProperty(name = "content", required = true)
    @NotBlank(message = "content is empty")
    private String content;

    @ApiModelProperty(name = "parentId", required = true)
    @NotNull(message = "parentId is null")
    private Integer parentId;

    @ApiModelProperty(name = "type", required = true)
    @NotNull(message = "type is null")
    private OntologyLemmaTypeEnum type;

    @ApiModelProperty(name = "orderIndex", required = false, value = "展示顺序", example = "1")
    private Integer orderIndex;

    @ApiModelProperty(name = "extraInfo", required = false)
    private String extraInfo;
}
