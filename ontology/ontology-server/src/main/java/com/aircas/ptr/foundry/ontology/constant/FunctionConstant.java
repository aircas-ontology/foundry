package com.aircas.ptr.foundry.ontology.constant;

/**
 * 函数算子相关常量
 */
public class FunctionConstant {

    /**
     * 发布状态：未发布（草稿，可修改/删除）
     */
    public static final int PUBLISH_UNPUBLISHED = 0;

    /**
     * 发布状态：已发布（锁定，只能复制为新版本后修改）
     */
    public static final int PUBLISH_PUBLISHED = 1;

    /**
     * 版本号格式：x.y.z（如 1.0.0），各段为非负整数
     */
    public static final String VERSION_PATTERN = "^\\d+\\.\\d+\\.\\d+$";

    private FunctionConstant() {
    }
}
