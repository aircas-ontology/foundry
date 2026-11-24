package com.aircas.ptr.foundry.topic.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TaskStatusEnum {

    RUNNING(0, "执行中"),
    FINISHED(1, "执行完成");

    private final int code;
    private final String label;
}
