package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.common.VisibilityWindow;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
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
@ApiModel(value = "自定义函数返回结果VO")
public class FunctionResultVO<T> {

    /**
     * 未来最近可见窗口开始时间
     */
    @ApiModelProperty(name = "startTime",value = "未来最近可见窗口开始时间",example = "1769585745344")
    private Date startTime;


    /**
     * 未来最近可见窗口结束时间
     */
    @ApiModelProperty(name = "endTime",value = "未来最近可见窗口结束时间",example = "1769585745344")
    private Date endTime;

    @ApiModelProperty(name = "data",value = "数据",example = "{\"a\":1}")
    private T data;

    @ApiModelProperty(name = "description",value = "描述")
    private String description;


    /**
     * 异步处理任务id
     */
    @ApiModelProperty(name = "taskId",value = "异步处理任务id",example = "task_1769585745344")
    private String taskId;

    /**
     * 一段时间范围内所有可见窗口列表
     */
    @ApiModelProperty(name = "timeWindows",value = "一段时间范围内所有可见窗口列表")
    private List<VisibilityWindow> timeWindows;

}
