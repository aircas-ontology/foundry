package com.aircas.ptr.foundry.topic.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "隐含关系总览统计汇总")
public class ImplicitRelationOverviewStatVO {

    @Schema(description = "关联类型")
    private String relationType;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "数量")
    private Integer quantity;
}
