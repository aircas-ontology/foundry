package com.aircas.ptr.foundry.ontology.entity.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * @author wangweigang
 * @descrption 概览页面数量统计vo
 */
@Data
@ApiModel(description = "概览数量统计vo")
public class OverviewCountVo {

    /**
     * 本体数量统计
     */
    @ApiModelProperty(notes = "本体数量统计")
    private Integer ontologyCount;

    /**
     * 关系数量统计
     */
    @ApiModelProperty(notes = "关系数量统计")
    private Integer linkCount;

    /**
     * 动作数量统计
     */
    @ApiModelProperty(notes = "行为数量统计")
    private Integer actionCount;

    /**
     * 函数数量统计
     */
    @ApiModelProperty(notes = "函数数量统计")
    private Integer functionCount;
}
