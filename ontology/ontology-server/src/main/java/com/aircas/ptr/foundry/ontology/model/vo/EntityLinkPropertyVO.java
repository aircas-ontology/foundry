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
     * link的unique identifier
     */
    @ApiModelProperty(name = "linkUniqueIdentifier",value = "link 唯一标识")
    private String linkUniqueIdentifier;

    /**
     * link的名称
     */
    @ApiModelProperty(name = "linkName",value = "关系名称")
    private String linkName;

    @ApiModelProperty(name = "primaryKey", value = "实体主键标识")
    private String primaryKey;


    @ApiModelProperty(name = "displayName", value = "实体展示名称")
    private String displayName;


}
