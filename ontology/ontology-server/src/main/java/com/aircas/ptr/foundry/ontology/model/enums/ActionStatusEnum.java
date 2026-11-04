package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 行为状态，落在 ontology_action.status。
 * <p>
 * 行为只有一个状态列，取 2 是为了不改动存量数据的含义：已发布沿用原来的 1，
 * 已失效沿用原来的 0（对应算子的 status 停用），草稿是新增状态。
 * 算子侧的草稿/发布在 function.publish（0 未发布 / 1 已发布），两边的“已发布”同为 1。
 */
@AllArgsConstructor
@Getter
public enum ActionStatusEnum {

    DRAFT(2, "草稿"),
    PUBLISHED(1, "已发布"),
    DISABLE(0, "已失效");

    private final int value;

    private final String name;

}
