package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorParallelPolicyEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 并行节点的配置。
 * <p>P1 单轮引擎串行执行全部子节点后按 policy 汇总，maxConcurrency 只保存不生效。</p>
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树并行节点配置")
public class BehaviorParallelConfig {

    @Schema(name = "policy", description = "成败判定策略", example = "ALL_SUCCESS")
    private BehaviorParallelPolicyEnum policy;

    @Schema(name = "maxConcurrency", description = "最大并发数，0 或空表示不限制；P1 只保存不生效")
    private Integer maxConcurrency;
}
