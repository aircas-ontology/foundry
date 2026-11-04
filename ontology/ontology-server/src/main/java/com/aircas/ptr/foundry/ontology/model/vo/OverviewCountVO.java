package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "概览数量统计")
public class OverviewCountVO {

    /**
     * 对象数量统计
     */
    @Schema(description = "对象空间统计")
    private Integer spaceCount;

    /**
     * 对象数量统计
     */
    @Schema(description = "对象数量统计")
    private Integer ontologyCount;

    /**
     * 分组数量统计
     */
    @Schema(description = "分组统计")
    private Integer groupCount;

    /**
     * 行为调度统计
     */
    @Schema(description = "行为调度统计")
    private Integer actionSchedulingCount;

    /**
     * 行为统计
     */
    @Schema(description = "行为统计")
    private Integer actionCount;

    /**
     * 函数数量统计
     */
    @Schema(description = "函数数量统计")
    private Integer functionCount;

    /**
     * 对象属性统计
     */
    @Schema(description = "对象属性统计")
    private Integer propertyCount;


    /**
     * 对象关系统计
     */
    @Schema(description = "对象关系统计")
    private Integer linkCount;
}
