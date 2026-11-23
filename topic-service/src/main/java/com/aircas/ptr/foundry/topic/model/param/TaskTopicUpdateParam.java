package com.aircas.ptr.foundry.topic.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Date;

@Data
@Schema(description = "编辑任务主题")
public class TaskTopicUpdateParam {

    @Schema(description = "任务编号", example = "TASK-001")
    @NotBlank(message = "任务编号不能为空")
    @Size(max = 64, message = "任务编号最长64个字符")
    private String taskCode;

    @Schema(description = "任务名称", example = "区域巡查")
    @NotBlank(message = "任务名称不能为空")
    @Size(max = 128, message = "任务名称最长128个字符")
    private String taskName;

    @Schema(description = "任务类型，由页面传入", example = "巡查")
    @NotBlank(message = "任务类型不能为空")
    @Size(max = 64, message = "任务类型最长64个字符")
    private String taskType;

    @Schema(description = "任务区域，由页面传入", example = "华东")
    @NotBlank(message = "任务区域不能为空")
    @Size(max = 128, message = "任务区域最长128个字符")
    private String taskRegion;

    @Schema(description = "任务开始时间")
    @NotNull(message = "任务开始时间不能为空")
    private Date startTime;

    @Schema(description = "任务结束时间")
    @NotNull(message = "任务结束时间不能为空")
    private Date endTime;

    @Schema(description = "任务描述")
    @Size(max = 2000, message = "任务描述最长2000个字符")
    private String description;

    @Schema(description = "关联本体空间，多个用逗号分割", example = "space-a,space-b")
    @Size(max = 1024, message = "关联本体空间最长1024个字符")
    private String ontologySpace;

    @Schema(description = "任务状态，由页面传入", example = "进行中")
    @NotBlank(message = "任务状态不能为空")
    @Size(max = 32, message = "任务状态最长32个字符")
    private String status;
}
