package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorLoopModeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 重复节点的配置。
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树重复节点配置")
public class BehaviorRepeaterConfig {

    @Schema(name = "loopMode", description = "循环模式", example = "FIXED_TIMES")
    private BehaviorLoopModeEnum loopMode;

    @Schema(name = "times", description = "循环次数，loopMode=FIXED_TIMES 时必填且大于 0")
    private Integer times;
}
