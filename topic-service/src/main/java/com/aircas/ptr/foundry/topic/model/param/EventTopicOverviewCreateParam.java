package com.aircas.ptr.foundry.topic.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "创建事件主题总览统计")
public class EventTopicOverviewCreateParam {

    @Schema(description = "事件主题编号", example = "1")
    @NotNull(message = "事件主题编号不能为空")
    private Long eventTopicId;

    @Schema(description = "类型", example = "海上")
    @NotBlank(message = "类型不能为空")
    @Size(max = 64, message = "类型最长64个字符")
    private String type;

    @Schema(description = "名称", example = "舰船")
    @NotBlank(message = "名称不能为空")
    @Size(max = 128, message = "名称最长128个字符")
    private String name;

    @Schema(description = "占比", example = "0.2500")
    @NotNull(message = "占比不能为空")
    private BigDecimal proportion;

    @Schema(description = "数量", example = "10")
    @NotNull(message = "数量不能为空")
    private Integer quantity;

    @Schema(description = "创建人，不传时默认为admin", example = "admin")
    @Size(max = 64, message = "创建人最长64个字符")
    private String creator;
}
