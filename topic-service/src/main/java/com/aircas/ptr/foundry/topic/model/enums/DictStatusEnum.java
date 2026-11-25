package com.aircas.ptr.foundry.topic.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DictStatusEnum {

    NORMAL(0, "正常"),
    DELETED(1, "删除");

    private final int code;
    private final String label;
}
