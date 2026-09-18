package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 向导步骤 5（build-relations）响应体。
 * <p>
 * 整体构建依据 reasoning 提到外层，不再内嵌到每个关系对象中；
 * relations 为不含 reasoning 的关系列表。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Schema(description = "本体关系构建结果（向导步骤5输出：外层整体依据 + 关系列表）")
public class WizardRelationsResultVO {

    @Schema(name = "reasoning", description = "整体构建依据：说明这批关系主要参考的数据源表及关系判定理由",
            example = "数据源参考 hms_target（海玛斯目标信息表）；结合已有对象清单判定新对象与发射控制系统存在协同关系")
    private String reasoning;

    @Schema(name = "relations", description = "构建出的关系列表（不含 reasoning）")
    private List<WizardRelationVO> relations;
}
