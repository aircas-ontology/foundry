package com.aircas.ptr.foundry.ontology.entity.bo;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
@ApiModel(description = "本体基础类")
public class OntologyBaseObjectBo {

    @ApiModelProperty(value = "本体api", required = true, example = "xtmb")
    private String api;

    @ApiModelProperty(value = "实体主键值", required = true, example = "7")
    private String primaryKey;
}
