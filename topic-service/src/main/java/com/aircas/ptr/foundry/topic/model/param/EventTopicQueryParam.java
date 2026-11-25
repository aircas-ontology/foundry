package com.aircas.ptr.foundry.topic.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "事件主题分页查询。条件都可以不传，不传时返回全部未删除数据")
public class EventTopicQueryParam {

    @Schema(description = "关键词，模糊匹配事件主题类别、事件名称、地理区域")
    @Size(max = 128, message = "关键词最长128个字符")
    private String keyword;

    @Schema(description = "页码，从1开始", example = "1")
    @Min(value = 1, message = "pageNum最小为1")
    private Integer pageNum = 1;

    @Schema(description = "每页条数", example = "10")
    @Min(value = 1, message = "pageSize最小为1")
    @Max(value = 200, message = "pageSize最大为200")
    private Integer pageSize = 10;
}
