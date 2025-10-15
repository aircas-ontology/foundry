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
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Accessors(chain = true)
@ApiModel(value = "实体关系属性VO")
public class EntityLinkPropertyVO {


    /**
     * link的名称
     */
    @ApiModelProperty(name = "linkName",value = "关系名称")
    private String linkName;

    @ApiModelProperty(name = "primaryKeyFrom", value = "开始实体主键标识")
    private Object primaryKeyFrom;

    @ApiModelProperty(name = "displayNameFrom", value = "开始实体展示名称")
    private String displayNameFrom;

    @ApiModelProperty(name = "primaryKeyTo", value = "结束实体主键标识")
    private Object primaryKeyTo;

    @ApiModelProperty(name = "displayNameTo", value = "结束实体展示名称")
    private String displayNameTo;

}
