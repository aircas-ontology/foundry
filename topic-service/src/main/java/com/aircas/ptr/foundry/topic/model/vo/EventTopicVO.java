package com.aircas.ptr.foundry.topic.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@Schema(description = "事件主题")
public class EventTopicVO {

    @Schema(description = "事件主题编号")
    private Long id;

    @Schema(description = "事件名称")
    private String eventName;

    @Schema(description = "事件主题类别")
    private String category;

    @Schema(description = "开始时间")
    private Date startTime;

    @Schema(description = "结束时间")
    private Date endTime;

    @Schema(description = "地理区域")
    private String geoRegion;

    @Schema(description = "区域点")
    private String regionPoint;

    @Schema(description = "事件主题描述")
    private String description;

    @Schema(description = "关联本体空间，多个用逗号分割")
    private String ontologySpace;

    @Schema(description = "状态：0执行中，1执行成功，2执行失败")
    private Integer status;
}
