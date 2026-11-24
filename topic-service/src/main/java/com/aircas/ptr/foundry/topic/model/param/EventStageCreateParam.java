package com.aircas.ptr.foundry.topic.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Date;

@Data
@Schema(description = "创建事件阶段")
public class EventStageCreateParam {

    @Schema(description = "任务编号", example = "1")
    @NotNull(message = "任务编号不能为空")
    private Long taskCode;

    @Schema(description = "事件阶段名称", example = "发现阶段")
    @NotBlank(message = "事件阶段名称不能为空")
    @Size(max = 128, message = "事件阶段名称最长128个字符")
    private String stageName;

    @Schema(description = "事件阶段开始时间")
    @NotNull(message = "事件阶段开始时间不能为空")
    private Date startTime;

    @Schema(description = "事件阶段结束时间")
    @NotNull(message = "事件阶段结束时间不能为空")
    private Date endTime;

    @Schema(description = "事件编号，多个用逗号分割")
    @Size(max = 1024, message = "事件编号最长1024个字符")
    private String eventCodes;

    @Schema(description = "创建人，不传时默认为admin", example = "admin")
    @Size(max = 64, message = "创建人最长64个字符")
    private String creator;
}
