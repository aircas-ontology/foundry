package com.aircas.ptr.foundry.topic.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
@Schema(description = "事件主题总览统计")
public class EventTopicOverviewVO {

    @Schema(description = "编号")
    private Long id;

    @Schema(description = "事件主题编号")
    private Long eventTopicId;

    @Schema(description = "类型")
    private String type;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "占比")
    private BigDecimal proportion;

    @Schema(description = "数量")
    private Integer quantity;
}
