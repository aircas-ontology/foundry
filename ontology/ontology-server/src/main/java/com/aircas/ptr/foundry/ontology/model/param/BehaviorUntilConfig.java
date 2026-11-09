package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorStateEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 直到成功、直到失败节点的配置。
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树直到成功/失败节点配置")
public class BehaviorUntilConfig {

    @Schema(name = "maxTimes", description = "最大重试次数，0 或空表示不重试；在单次执行内同步重试", example = "2")
    private Integer maxTimes;

    @Schema(name = "finishState", description = "次数耗尽仍未达成时的收尾状态，只能是 SUCCESS 或 FAILURE", example = "FAILURE")
    private BehaviorStateEnum finishState;
}
