package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.common.VisibilityWindow;
import io.swagger.annotations.ApiModel;
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
    private Date startTime;


    /**
     * 未来最近可见窗口结束时间
     */
    private Date endTime;

    private T data;

    private String description;

    /**
     * 一段时间范围内所有可见窗口列表
     */
    private List<VisibilityWindow> timeWindows;

}
