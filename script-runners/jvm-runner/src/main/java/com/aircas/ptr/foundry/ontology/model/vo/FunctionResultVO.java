package com.aircas.ptr.foundry.ontology.model.vo;

import java.util.Date;

/**
 * 兼容存量 Groovy 脚本的结果类型（与 ontology-server 的 FunctionResultVO 保持相同全限定名与核心字段）。
 * <p>
 * groovy-runner 内提供该类，使历史脚本 {@code FunctionResultVO handle(...)} 仍能在 runner 中编译通过；
 * 执行结果由 runner 原样序列化为 JSON 返回，主应用再反序列化为其自身的 FunctionResultVO。
 * 注：仅覆盖核心字段（data/description/taskId/startTime/endTime），不含 timeWindows 等扩展字段。
 */
public class FunctionResultVO<T> {

    private Date startTime;

    private Date endTime;

    private T data;

    private String description;

    private String taskId;

    public FunctionResultVO() {
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

}
