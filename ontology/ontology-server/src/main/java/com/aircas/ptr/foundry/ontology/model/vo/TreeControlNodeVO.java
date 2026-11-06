package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorNodeCategoryEnum;
import com.aircas.ptr.foundry.ontology.model.enums.BehaviorNodeKindEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

/**
 * 行为树节点类型定义，前端按它渲染节点面板与连线约束。
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树节点类型")
public class TreeControlNodeVO {

    @Schema(name = "kind", description = "节点类型", example = "SEQUENCE")
    private BehaviorNodeKindEnum kind;

    @Schema(name = "name", description = "节点名称", example = "顺序节点")
    private String name;

    @Schema(name = "category", description = "节点类别：COMPOSITE 容器 / DECORATOR 装饰器 / LEAF 叶子", example = "COMPOSITE")
    private BehaviorNodeCategoryEnum category;

    @Schema(name = "maxChildren", description = "子节点上限，-1 表示不限", example = "-1")
    private Integer maxChildren;
}
