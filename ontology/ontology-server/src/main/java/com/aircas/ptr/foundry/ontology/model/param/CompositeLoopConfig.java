package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

/**
 * 循环节点配置。
 * <p>foreach 模式按 {@code collectionPath} 从上游数据中取集合逐项迭代；
 * times 模式按 {@code times} 固定次数迭代。</p>
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "函数编排循环节点配置")
public class CompositeLoopConfig {

    @Schema(name = "mode", description = "循环模式：foreach（按集合迭代）/times（按次数迭代）", example = "foreach")
    private String mode;

    @Schema(name = "collectionPath", description = "foreach 模式下集合的取值路径，如 data 或 a.b", example = "data")
    private String collectionPath;

    @Schema(name = "times", description = "times 模式下的循环次数", example = "3")
    private Integer times;

    @Schema(name = "itemName", description = "当前迭代项在循环体上下文中的名称", example = "item")
    private String itemName;
}