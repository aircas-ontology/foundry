package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.TaskStatusEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
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
@ApiModel(value = "调度结果查询VO")
public class SchedulingResultVO {

    @ApiModelProperty(name = "msg",value = "日志消息")
    private String msg;

    @ApiModelProperty(name = "triggerTime",value = "触发时间")
    private Date triggerTime;

    @ApiModelProperty(name = "requestParam",value = "请求参数")
    private String requestParam;

    @ApiModelProperty(name = "completeTime",value = "完成时间")
    private Date completeTime;

    @ApiModelProperty(name = "taskStatus",value = "执行状态")
    private TaskStatusEnum taskStatus;
}
