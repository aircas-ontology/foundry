package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorStateEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 限时节点的配置。
 * <p>限时是协作式的：已经开始的行为无法中断，超时后不再发起新的行为，并按 timeoutState 上报。</p>
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树限时节点配置")
public class BehaviorTimeLimitConfig {

    @Schema(name = "limitSeconds", description = "限时时长（秒），必须大于 0")
    private Integer limitSeconds;

    @Schema(name = "timeoutState", description = "超时后本节点上报的状态，只能是 SUCCESS 或 FAILURE", example = "FAILURE")
    private BehaviorStateEnum timeoutState;
}
