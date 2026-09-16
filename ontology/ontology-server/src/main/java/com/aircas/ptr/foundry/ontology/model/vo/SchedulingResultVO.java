package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.TaskStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;

/**
 * @author LiuYue
 * @date 2026/4/21
 * @description
 */
@Data
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "调度结果查询VO")
public class SchedulingResultVO {

    @Schema(name = "msg",description = "日志消息")
    private String msg;

    @Schema(name = "triggerTime",description = "触发时间")
    private Date triggerTime;

    @Schema(name = "requestParam",description = "请求参数")
    private String requestParam;

    @Schema(name = "completeTime",description = "完成时间")
    private Date completeTime;

    @Schema(name = "taskStatus",description = "执行状态")
    private TaskStatusEnum taskStatus;
}
