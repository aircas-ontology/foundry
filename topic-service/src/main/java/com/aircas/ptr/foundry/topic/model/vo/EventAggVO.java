package com.aircas.ptr.foundry.topic.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@Schema(description = "聚合事件")
public class EventAggVO {

    @Schema(description = "聚合事件编号")
    private Long id;

    @Schema(description = "事件编号")
    private String eventNo;

    @Schema(description = "事件标题")
    private String eventTitle;

    @Schema(description = "事件发生时间")
    private Date eventTime;

    @Schema(description = "事件状态")
    private String status;

    @Schema(description = "进程描述")
    private String process;

    @Schema(description = "事件位置")
    private String location;

    @Schema(description = "事件经度")
    private Double eventLon;

    @Schema(description = "事件纬度")
    private Double eventLat;

    @Schema(description = "情报来源类型")
    private String sourceType;

    @Schema(description = "情报来源平台")
    private String sourcePlatform;

    @Schema(description = "置信度")
    private String confidence;

    @Schema(description = "事件内容")
    private String eventContent;
}
