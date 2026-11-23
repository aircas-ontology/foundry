package com.aircas.ptr.foundry.topic.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

@Data
@Schema(description = "任务主题列表查询")
public class TaskTopicQueryParam {

    @Schema(description = "任务名称，模糊匹配")
    @Size(max = 128, message = "任务名称最长128个字符")
    private String taskName;

    @Schema(description = "任务类型")
    @Size(max = 64, message = "任务类型最长64个字符")
    private String taskType;

    @Schema(description = "任务区域")
    @Size(max = 128, message = "任务区域最长128个字符")
    private String taskRegion;

    @Schema(description = "任务开始时间，查询该时间及之后开始的任务")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Date startTime;

    @Schema(description = "任务结束时间，查询该时间及之前结束的任务")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Date endTime;
}
