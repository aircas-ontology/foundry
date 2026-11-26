package com.aircas.ptr.foundry.topic.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "事件主题总览统计汇总")
public class EventTopicOverviewStatVO {

    @Schema(description = "类型")
    private String type;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "数量")
    private Integer quantity;
}
