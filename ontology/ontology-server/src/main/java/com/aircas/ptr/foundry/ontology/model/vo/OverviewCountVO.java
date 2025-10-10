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
