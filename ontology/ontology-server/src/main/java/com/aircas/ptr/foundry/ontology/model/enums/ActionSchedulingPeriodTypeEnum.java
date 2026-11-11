package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 周期调度类型：对应前端执行周期 每天/每周/每月/Cron。
 */
@Getter
@AllArgsConstructor
public enum ActionSchedulingPeriodTypeEnum {

    DAILY("每天"),
    WEEKLY("每周"),
    MONTHLY("每月"),
    CRON("Cron");

    private final String description;
}