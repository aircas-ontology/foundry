package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorParallelPolicyEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 并行节点的配置。
 * <p>所有子节点在单次执行内并发跑完，再按 policy 汇总；maxConcurrency 限制同时在跑的分支数，
 * 单个节点最多 8 路。分支共享运行时黑板，写同一个键以最后写入为准。</p>
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树并行节点配置")
public class BehaviorParallelConfig {

    @Schema(name = "policy", description = "成败判定策略", example = "ALL_SUCCESS")
    private BehaviorParallelPolicyEnum policy;

    @Schema(name = "maxConcurrency", description = "最大并发数，空表示按子节点数并发；单节点最多 8 路")
    private Integer maxConcurrency;
}
