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
@ApiModel(value = "实体属关系VO")
public class EntityLinkVO {


    @ApiModelProperty(name = "links", value = "实体关联关系")
    private List<EntityLinkPropertyVO> links;


    /**
     * link的unique identifier
     */
    @ApiModelProperty(name = "primaryKey", value = "实体主键标识")
    private String primaryKey;


    @ApiModelProperty(name = "displayName", value = "实体展示名称")
    private String displayName;


}
