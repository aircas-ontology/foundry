package com.aircas.ptr.foundry.topic.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@Schema(description = "事件阶段")
public class EventStageVO {

    @Schema(description = "事件阶段编号")
    private Long id;

    @Schema(description = "任务编号")
    private Long taskCode;

    @Schema(description = "事件阶段名称")
    private String stageName;

    @Schema(description = "事件阶段开始时间")
    private Date startTime;

    @Schema(description = "事件阶段结束时间")
    private Date endTime;

    @Schema(description = "事件数量")
    private Integer eventCount;

    @Schema(description = "事件编号，多个用逗号分割")
    private String eventCodes;
}
