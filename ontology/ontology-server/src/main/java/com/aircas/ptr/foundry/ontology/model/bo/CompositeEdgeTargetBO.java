package com.aircas.ptr.foundry.ontology.model.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 编排节点之间的一条数据连线在编译后的形态。
 */
@Data
@Schema(name = "CompositeEdgeTargetBO", description = "编排数据连线目标")
public class CompositeEdgeTargetBO {

    @Schema(description = "目标节点 LiteFlow 节点 id")
    private String targetElId;

    @Schema(description = "目标节点的入参名")
    private String targetParamName;

    @Schema(description = "本节点的出参 id")
    private String sourceParamId;

    public CompositeEdgeTargetBO() {
    }

    public CompositeEdgeTargetBO(String targetElId, String targetParamName, String sourceParamId) {
        this.targetElId = targetElId;
        this.targetParamName = targetParamName;
        this.sourceParamId = sourceParamId;
    }
}
