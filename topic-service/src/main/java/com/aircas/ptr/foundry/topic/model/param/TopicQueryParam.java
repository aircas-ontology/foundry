package com.aircas.ptr.foundry.topic.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
@Schema(description = "主题分页查询")
public class TopicQueryParam {

    @Schema(description = "名称，模糊匹配")
    private String name;

    @Schema(description = "页码，从1开始", example = "1")
    @Min(value = 1, message = "pageNum最小为1")
    private Integer pageNum = 1;

    @Schema(description = "每页条数", example = "10")
    @Min(value = 1, message = "pageSize最小为1")
    @Max(value = 200, message = "pageSize最大为200")
    private Integer pageSize = 10;
}
