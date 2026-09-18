package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.common.VisibilityWindow;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(description = "自定义函数返回结果VO")
public class FunctionResultVO<T> {

    /**
     * 未来最近可见窗口开始时间
     */
    @Schema(name = "startTime",description = "未来最近可见窗口开始时间",example = "1769585745344")
    private Date startTime;


    /**
     * 未来最近可见窗口结束时间
     */
    @Schema(name = "endTime",description = "未来最近可见窗口结束时间",example = "1769585745344")
    private Date endTime;

    @Schema(name = "data",description = "数据",example = "{\"a\":1}")
    private T data;

    @Schema(name = "description",description = "描述")
    private String description;


    /**
     * 异步处理任务id
     */
    @Schema(name = "taskId",description = "异步处理任务id",example = "task_1769585745344")
    private String taskId;

    /**
     * 一段时间范围内所有可见窗口列表
     */
    @Schema(name = "timeWindows",description = "一段时间范围内所有可见窗口列表")
    private List<VisibilityWindow> timeWindows;

}
