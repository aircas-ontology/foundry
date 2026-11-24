package com.aircas.ptr.foundry.topic.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EventTopicStatusEnum {

    RUNNING(0, "执行中"),
    SUCCESS(1, "执行成功"),
    FAILED(2, "执行失败");

    private final int code;
    private final String label;
}
