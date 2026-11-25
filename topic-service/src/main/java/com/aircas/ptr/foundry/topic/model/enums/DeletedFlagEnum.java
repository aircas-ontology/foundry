package com.aircas.ptr.foundry.topic.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DeletedFlagEnum {

    NOT_DELETED(0),
    DELETED(1);

    private final int code;
}
