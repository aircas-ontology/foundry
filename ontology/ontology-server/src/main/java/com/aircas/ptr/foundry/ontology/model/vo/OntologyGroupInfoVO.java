package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/15 11:16
 */

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "本体分组VO")
public class OntologyGroupInfoVO {

     @ApiModelProperty(value = "分组名称", example = "远海远域")
     private String groupName;

     @ApiModelProperty(value = "分组描述", example = "远海远域")
     private String description;

     @ApiModelProperty(value = "分组id", example = "fdsafdsfaf")
     private String groupId;

     @ApiModelProperty(value = "分组icon url", example = "icon")
     private String icon;

     @ApiModelProperty(name = "spaceId", value = "本体空间id")
     private Integer spaceId;

}
