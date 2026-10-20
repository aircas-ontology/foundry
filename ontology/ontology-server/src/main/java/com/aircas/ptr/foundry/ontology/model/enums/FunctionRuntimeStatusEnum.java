package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 函数算子运行状态（内存级实时跟踪）：
 * 执行入口 add → 执行结束 remove，列表查询时据此填充。
 */
@AllArgsConstructor
@Getter
public enum FunctionRuntimeStatusEnum {

    RUNNING(1, "运行中"),
    NOT_RUNNING(0, "未运行");

    private final int code;
    private final String desc;
}
