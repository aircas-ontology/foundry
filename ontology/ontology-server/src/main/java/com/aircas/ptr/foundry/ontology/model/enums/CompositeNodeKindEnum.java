package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 函数编排节点类型。
 * <p>与原型 FunctionOperatorCompositeNodeKind 对应，未显式指定 kind 的节点按 function 处理。</p>
 */
@AllArgsConstructor
@Getter
public enum CompositeNodeKindEnum {

    FUNCTION(1, "函数节点"),
    LOOP(2, "循环节点"),
    BRANCH(3, "分支节点"),
    TRANSFORM(4, "转换节点");

    private final int value;

    private final String name;

    /**
     * 兼容前端传入的小写字符串（function/loop/branch/transform）。
     *
     * @param kind 节点类型字符串，为空时按 function 处理
     * @return 对应枚举
     */
    public static CompositeNodeKindEnum fromKind(String kind) {
        if (kind == null || kind.trim().isEmpty()) {
            return FUNCTION;
        }
        String normalized = kind.trim().toUpperCase();
        for (CompositeNodeKindEnum item : values()) {
            if (item.name().equalsIgnoreCase(normalized)) {
                return item;
            }
        }
        return FUNCTION;
    }

    /**
     * 与原型 resolveCompositeNodeKind 对齐：kind 命中已知类型时以 kind 为准，
     * 否则（kind 为空或为无法识别的历史值）按 functionId 回退识别逻辑节点，
     * 避免只带 functionId 的逻辑节点被当成引用外部函数的节点。
     *
     * @param kind       节点类型字符串
     * @param functionId 节点引用的函数 api
     * @return 节点类型
     */
    public static CompositeNodeKindEnum fromKindAndFunctionId(String kind, String functionId) {
        if (isKnownKind(kind)) {
            return fromKind(kind);
        }
        if (functionId == null || functionId.trim().isEmpty()) {
            return FUNCTION;
        }
        String api = functionId.trim();
        if (CompositeLogicIds.LOOP.equals(api)) {
            return LOOP;
        }
        if (CompositeLogicIds.BRANCH.equals(api)) {
            return BRANCH;
        }
        if (CompositeLogicIds.TRANSFORM.equals(api)) {
            return TRANSFORM;
        }
        return FUNCTION;
    }

    /** kind 是否为原型已知的四种取值 */
    public static boolean isKnownKind(String kind) {
        if (kind == null || kind.trim().isEmpty()) {
            return false;
        }
        String normalized = kind.trim().toUpperCase();
        for (CompositeNodeKindEnum item : values()) {
            if (item.name().equalsIgnoreCase(normalized)) {
                return true;
            }
        }
        return false;
    }
}