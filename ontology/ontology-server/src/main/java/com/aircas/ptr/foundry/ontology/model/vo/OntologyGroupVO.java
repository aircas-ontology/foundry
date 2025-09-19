package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @author dongjunchuan
 * @description
 * @since 2023/12/15 11:16
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "本体分组视图")
public class OntologyGroupVO {

     @ApiModelProperty(value = "分组名称", example = "远海远域")
     private String groupName;

     @ApiModelProperty(value = "分组id", example = "fdsafdsfaf")
     private String groupId;

     @ApiModelProperty(value = "分组数量", example = "1")
     private Integer count;
}
