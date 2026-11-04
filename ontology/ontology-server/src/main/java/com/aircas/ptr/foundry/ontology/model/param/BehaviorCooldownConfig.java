package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorStateEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 冷却节点的配置。
 * <p>冷却窗口要记住上次执行时间，而变量作用域只有 RUNTIME、不跨执行持久，
 * 所以 P1 只校验并保存配置，执行时 hitResult 恒按未命中冷却处理。</p>
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树冷却节点配置")
public class BehaviorCooldownConfig {

    @Schema(name = "intervalSeconds", description = "冷却时长（秒），必须大于 0")
    private Integer intervalSeconds;

    @Schema(name = "hitResult", description = "处于冷却期时本节点上报的状态", example = "FAILURE")
    private BehaviorStateEnum hitResult;
}
