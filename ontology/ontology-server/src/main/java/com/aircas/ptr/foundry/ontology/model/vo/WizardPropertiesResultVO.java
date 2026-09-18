package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.dto.WizardPropertyDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 向导步骤 4（build-properties）响应体。
 * <p>
 * 整体构建依据 reasoning 提到外层，不再内嵌到每个属性对象中；
 * properties 为不含 reasoning 的属性列表。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Schema(description = "本体属性构建结果（向导步骤4输出：外层整体依据 + 属性列表）")
public class WizardPropertiesResultVO {

    @Schema(name = "reasoning", description = "整体构建依据：说明这批属性主要源自哪些数据源表及字段",
            example = "本次属性主要源自 hms_target（海玛斯目标信息表）的 target_code、target_name 等字段")
    private String reasoning;

    @Schema(name = "properties", description = "构建出的属性列表（不含 reasoning）")
    private List<WizardPropertyDTO> properties;
}
