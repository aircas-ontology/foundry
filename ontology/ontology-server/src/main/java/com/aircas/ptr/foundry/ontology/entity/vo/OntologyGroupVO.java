package com.aircas.ptr.foundry.ontology.entity.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.models.auth.In;
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

     private Long id;

     @ApiModelProperty(value = "软删除状态位，1有效，0无效", example = "1")
     private Integer status;

     @ApiModelProperty(value = "记录创建时间", example = "2021-10-10 00:00:00")
     private Date createTime;

     @ApiModelProperty(value = "记录修改时间", example = "2021-10-10 00:00:00")
     private Date updateTime;

     @ApiModelProperty(value = "分组名称", example = "远海远域")
     private String groupName;

     @ApiModelProperty(value = "分组id", example = "fdsafdsfaf")
     private String groupId;

     @ApiModelProperty(value = "分组数量", example = "1")
     private Integer count;
}
