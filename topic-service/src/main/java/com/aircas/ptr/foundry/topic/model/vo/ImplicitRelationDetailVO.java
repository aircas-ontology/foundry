package com.aircas.ptr.foundry.topic.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "隐含关系详情")
public class ImplicitRelationDetailVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "起始节点编号")
    private String startNodeCode;

    @Schema(description = "起始节点名称")
    private String startNodeName;

    @Schema(description = "所属空间")
    private String belongSpace;

    @Schema(description = "关联类型")
    private String relationType;

    @Schema(description = "关系跳数")
    private Integer hopCount;
}
