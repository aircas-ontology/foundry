package com.aircas.ptr.foundry.topic.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
@Schema(description = "抽取列表项")
public class ExtractItemVO {

    @Schema(description = "名称")
    private String name;

    @Schema(description = "数量")
    private Integer quantity;

    @Schema(description = "占比")
    private BigDecimal proportion;
}
