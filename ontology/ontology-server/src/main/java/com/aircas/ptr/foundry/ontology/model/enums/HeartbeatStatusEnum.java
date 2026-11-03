package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 容器心跳状态：TCP 探活结果。
 */
@AllArgsConstructor
@Getter
public enum HeartbeatStatusEnum {

    HEALTHY("健康"),
    UNHEALTHY("异常");

    private final String label;
}
