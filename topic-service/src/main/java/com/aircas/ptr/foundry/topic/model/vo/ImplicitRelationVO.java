package com.aircas.ptr.foundry.topic.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
@Builder
@Schema(description = "隐含关系列表")
public class ImplicitRelationVO {

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

    @Schema(description = "隐含关系数")
    private Integer implicitCount;

    @Schema(description = "进度状态：0未发现，1待校验，2已完成")
    private Integer progressStatus;

    @Schema(description = "更新时间")
    private Date updateTime;

    @Schema(description = "隐含关系总览统计。相同关联类型和名称的数量已求和")
    private List<ImplicitRelationOverviewStatVO> overviewStats;
}
