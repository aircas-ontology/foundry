package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author dongjunchuan
 * @description 可见性，1正常、2隐藏、3突出显示
 * @since 2023/12/18 9:50
 */

@AllArgsConstructor
@Getter
public enum Visibility {

    NORMAL(1, "正常"),
    HIDDEN(2, "隐藏"),
    PROMINENT(3, "突出显示");

    private final int value;
    private final String name;
}
