package com.aircas.ptr.foundry.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author dongjunchuan
 * @description 本体及数据源的索引状态，1成功、2失败、3未开始
 * @since 2023/12/18 9:50
 */

@AllArgsConstructor
@Getter
public enum IndexStatus {

    SUCCESS(1, "成功"),
    FAILED(2, "失败"),
    NOT_STARTED(3, "未开始");

    private final int value;
    private final String name;
}
