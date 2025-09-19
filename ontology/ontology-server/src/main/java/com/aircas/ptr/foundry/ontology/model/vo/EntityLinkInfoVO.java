package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;


@Data
@Builder
@Accessors(chain = true)
@ApiModel(value = "实体关系数据信息")
public class EntityLinkInfoVO {

    /**
     * link的名称
     */
    @ApiModelProperty(name = "name",value = "关系名称")
    private String name;


    /**
     * 开始实体的id
     */
    @ApiModelProperty(name = "entityIdFrom",value = "开始实体id")
    private String entityIdFrom;

    /**
     * 开始实体名称
     */
    @ApiModelProperty(name = "ontologyNameFrom",value = "开始实体名称")
    private String entityNameFrom;


    /**
     * 结束实体的id
     */
    @ApiModelProperty(name = "entityIdTo",value = "结束实体id")
    private String entityIdTo;

    /**
     * 结束实体名称
     */
    @ApiModelProperty(name = "entityNameTo",value = "结束实体名称")
    private String entityNameTo;



}
