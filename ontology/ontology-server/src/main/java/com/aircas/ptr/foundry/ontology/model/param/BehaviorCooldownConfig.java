package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorStateEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 冷却节点的配置。
 * <p>冷却状态按「树 + 节点 + 执行实例主键」持久化，跨请求、跨实例有效；距上次执行未满 intervalSeconds 时
 * 不执行子节点，直接上报 hitResult。计时从子节点开始执行算起，子节点失败也不退还冷却时间。</p>
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树冷却节点配置")
public class BehaviorCooldownConfig {

    @Schema(name = "intervalSeconds", description = "冷却时长（秒），必须大于 0")
    private Integer intervalSeconds;

    @Schema(name = "hitResult", description = "处于冷却期时本节点上报的状态，只能是 SUCCESS 或 FAILURE", example = "FAILURE")
    private BehaviorStateEnum hitResult;
}
