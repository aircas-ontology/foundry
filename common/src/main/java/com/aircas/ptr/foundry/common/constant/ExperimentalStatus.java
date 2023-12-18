package com.aircas.ptr.foundry.common.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author dongjunchuan
 * @description 实验状态，1激活、2测试中、3废弃
 * @since 2023/12/18 9:50
 */

@AllArgsConstructor
@Getter
public enum ExperimentalStatus {

    ACTIVE(1, "激活"),
    EXPERIMENTAL(2, "测试中"),
    DEPRECATED(3, "废弃");

    private final int value;
    private final String name;
}
