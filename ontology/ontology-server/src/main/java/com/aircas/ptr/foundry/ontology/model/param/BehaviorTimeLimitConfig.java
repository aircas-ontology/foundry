package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorStateEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 限时节点的配置。
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树限时节点配置")
public class BehaviorTimeLimitConfig {

    @Schema(name = "limitSeconds", description = "限时时长（秒），必须大于 0")
    private Integer limitSeconds;

    @Schema(name = "timeoutState", description = "超时后本节点上报的状态", example = "FAILURE")
    private BehaviorStateEnum timeoutState;
}
