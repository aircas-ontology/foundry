package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 本体空间类型：区分普通空间与主题。
 */
@Getter
@AllArgsConstructor
public enum SpaceTypeEnum {

    SPACE(0, "空间"),
    TOPIC(1, "主题"),
    ;

    private final Integer value;
    private final String name;

    /**
     * 归一化类型：为空或非法值时按空间处理。
     */
    public static Integer normalize(Integer value) {
        return TOPIC.getValue().equals(value) ? TOPIC.getValue() : SPACE.getValue();
    }
}
