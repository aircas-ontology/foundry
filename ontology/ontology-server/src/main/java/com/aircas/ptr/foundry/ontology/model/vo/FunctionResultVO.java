package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.common.VisibilityWindow;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
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

    /**
     * 脚本自定义返回键透传容器。
     * 多语言脚本的返回值结构由用户代码决定（如 status 等字段），已知字段之外的键不丢弃：
     * 反序列化时由 {@link JsonAnySetter} 收集，序列化时由 {@link JsonAnyGetter} 平铺回顶层，保证原样回传前端。
     * 字段本身 @JsonIgnore（不作为嵌套的 extra 属性输出），any-getter 方法故意不用 getXxx 命名，避开属性拆分规则。
     */
    @Builder.Default
    @JsonIgnore
    private Map<String, Object> extra = new LinkedHashMap<>();

    @JsonAnyGetter
    public Map<String, Object> extraFields() {
        return extra == null ? new LinkedHashMap<>() : extra;
    }

    @JsonAnySetter
    public void putExtra(String key, Object value) {
        if (extra == null) {
            extra = new LinkedHashMap<>();
        }
        extra.put(key, value);
    }

}
