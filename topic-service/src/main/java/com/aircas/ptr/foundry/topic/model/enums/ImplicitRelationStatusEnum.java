package com.aircas.ptr.foundry.topic.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ImplicitRelationStatusEnum {

    NOT_FOUND(0, "未发现"),
    PENDING(1, "待校验"),
    FINISHED(2, "已完成");

    private final int code;
    private final String label;
}
