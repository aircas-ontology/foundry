package com.aircas.ptr.foundry.ontology.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 行为状态，落在 ontology_action.status。
 * <p>
 * 行为只有两态：草稿(2) 和 已发布(1)。
 * 新建行为默认草稿，需要显式发布才对外可用（智能体、调度）。
 * 算子侧的草稿/发布在 function.publish（0 未发布 / 1 已发布），两边的"已发布"同为 1。
 */
@AllArgsConstructor
@Getter
public enum ActionStatusEnum {

    DRAFT(2, "草稿"),
    PUBLISHED(1, "已发布");

    private final int value;

    private final String name;

}
