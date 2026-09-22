package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(description = "本体空间资源统计")
public class OntologySpaceStatisticVO {

    @Schema(name = "spaceId", description = "本体空间id")
    private Integer spaceId;

    @Schema(name = "ontologyCount", description = "对象（本体）数量")
    private Integer ontologyCount;

    @Schema(name = "linkCount", description = "关系数量")
    private Integer linkCount;

    @Schema(name = "functionCount", description = "函数算子数量")
    private Integer functionCount;

    @Schema(name = "actionCount", description = "行为数量")
    private Integer actionCount;

    @Schema(name = "actionSchedulingCount", description = "行为调度数量（规则+任务）")
    private Integer actionSchedulingCount;
}
