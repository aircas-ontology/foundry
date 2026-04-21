package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ScheduleStatus {

    START(1, "开启"),
    STOP(0, "暂停"),
    ;

    private final int value;
    private final String name;
}
