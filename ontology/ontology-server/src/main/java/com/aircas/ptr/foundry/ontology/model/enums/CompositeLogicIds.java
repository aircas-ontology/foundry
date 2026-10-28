package com.aircas.ptr.foundry.ontology.model.enums;

/**
 * 原型画布逻辑节点的约定 functionId（前端创建逻辑节点时会同时写入 kind 与 functionId，
 * 历史数据可能只保留 functionId）。
 *
 * @see CompositeNodeKindEnum#fromKindAndFunctionId(String, String)
 */
public final class CompositeLogicIds {

    /** 循环节点 */
    public static final String LOOP = "__logic-loop__";

    /** 条件分支节点 */
    public static final String BRANCH = "__logic-branch__";

    /** 数据格式转换节点 */
    public static final String TRANSFORM = "__logic-transform__";

    /** Switch Case 节点 */
    public static final String SWITCH = "__logic-switch__";

    /** 逻辑节点统一的函数版本 */
    public static final String LOGIC_VERSION = "logic";

    private CompositeLogicIds() {
    }
}
