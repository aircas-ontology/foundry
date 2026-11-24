package com.aircas.ptr.foundry.topic.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@Schema(description = "任务主题详情")
public class TaskTopicDetailVO {

    @Schema(description = "任务编号")
    private Long taskCode;

    @Schema(description = "任务名称")
    private String taskName;

    @Schema(description = "任务类型")
    private String taskType;

    @Schema(description = "任务区域")
    private String taskRegion;

    @Schema(description = "区域点位")
    private String regionPoint;

    @Schema(description = "任务开始时间")
    private Date startTime;

    @Schema(description = "任务结束时间")
    private Date endTime;

    @Schema(description = "任务描述")
    private String description;

    @Schema(description = "关联本体空间，多个用逗号分割")
    private String ontologySpace;

    @Schema(description = "任务状态")
    private String status;

    @Schema(description = "创建人")
    private String creator;

    @Schema(description = "创建时间")
    private Date createTime;
}
