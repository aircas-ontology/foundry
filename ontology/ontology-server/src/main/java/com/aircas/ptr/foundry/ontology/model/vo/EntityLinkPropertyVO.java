package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
@ApiModel(value = "实体关系属性VO")
public class EntityLinkPropertyVO {


    /**
     * link的名称
     */
    @ApiModelProperty(name = "linkName", value = "关系名称")
    private String linkName;

    @ApiModelProperty(name = "ontologyFrom", value = "本体开始id")
    private String ontologyFrom;

    @ApiModelProperty(name = "ontologyTo", value = "本体结束id")
    private String ontologyTo;

    @ApiModelProperty(name = "entityPrimaryKeyFrom", value = "开始实体主键值")
    private Object entityPrimaryKeyFrom;

    @ApiModelProperty(name = "entityPrimaryKeyTo", value = "结束实体主键值")
    private Object entityPrimaryKeyTo;

    @ApiModelProperty(name = "entityNodeFrom", value = "开始实体标识")
    private String entityNodeFrom;

    @ApiModelProperty(name = "displayNameFrom", value = "开始实体展示名称")
    private String displayNameFrom;

    @ApiModelProperty(name = "entityNodeTo", value = "结束实体标识")
    private String entityNodeTo;

    @ApiModelProperty(name = "displayNameTo", value = "结束实体展示名称")
    private String displayNameTo;

}
