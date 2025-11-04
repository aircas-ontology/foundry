package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "概览数量统计")
public class OverviewCountVO {

    /**
     * 本体数量统计
     */
    @ApiModelProperty(notes = "本体数量统计")
    private Integer ontologyCount;

    /**
     * 分组数量统计
     */
    @ApiModelProperty(notes = "分组统计")
    private Integer groupCount;

    /**
     * 行为调度统计
     */
    @ApiModelProperty(notes = "行为调度统计")
    private Integer actionSchedulingCount;

    /**
     * 函数数量统计
     */
    @ApiModelProperty(notes = "函数数量统计")
    private Integer functionCount;

    /**
     * 本体属性统计
     */
    @ApiModelProperty(notes = "本体属性统计")
    private Integer propertyCount;


    /**
     * 本体关系统计
     */
    @ApiModelProperty(notes = "本体关系统计")
    private Integer linkCount;
}
