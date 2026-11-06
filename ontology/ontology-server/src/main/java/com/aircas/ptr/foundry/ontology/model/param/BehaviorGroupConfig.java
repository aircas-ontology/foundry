package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorRunModeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 选择节点、顺序节点的配置。
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树容器节点（选择/顺序）配置")
public class BehaviorGroupConfig {

    @Schema(name = "runMode", description = "执行模式", example = "STANDARD")
    private BehaviorRunModeEnum runMode;
}
