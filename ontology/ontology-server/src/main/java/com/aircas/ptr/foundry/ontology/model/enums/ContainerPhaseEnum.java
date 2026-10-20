package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 容器算子运行实例的启停状态（持久化按枚举 name，前端标签映射 created/running/...）。
 */
@AllArgsConstructor
@Getter
public enum ContainerPhaseEnum {

    /** 已创建，容器尚未启动 */
    CREATED("已创建"),
    /** 运行中 */
    RUNNING("运行中"),
    /** 停止中 */
    STOPPING("停止中"),
    /** 已停止 */
    STOPPED("已停止"),
    /** 重启中 */
    RESTARTING("重启中"),
    /** 失败（启动失败或容器异常退出） */
    FAILED("失败");

    private final String label;

    /** 是否处于可启动态（created/stopped/failed） */
    public boolean canStart() {
        return this == CREATED || this == STOPPED || this == FAILED;
    }

    /** 是否处于可停止/重启态（running/restarting） */
    public boolean canStop() {
        return this == RUNNING || this == RESTARTING;
    }
}
