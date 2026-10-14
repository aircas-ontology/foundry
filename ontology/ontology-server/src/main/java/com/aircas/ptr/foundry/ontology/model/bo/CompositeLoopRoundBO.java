package com.aircas.ptr.foundry.ontology.model.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个循环的逐轮重放登记项：由 {@code CompositeElCompiler} 在生成 EL 时推导，
 * 运行期由 {@code CompositeRuntimeRequest} 按取数节点登记。
 */
@Data
@Schema(name = "CompositeLoopRoundBO", description = "编排循环逐轮重放登记项")
public class CompositeLoopRoundBO {

    @Schema(description = "循环节点 LiteFlow 节点 id")
    private String loopElId;

    @Schema(description = "循环体取数节点 LiteFlow 节点 id")
    private String preloadElId;

    @Schema(description = "取数节点的运行期定义，画布上没有该节点，由编译器生成")
    private CompositeNodePlanBO preloadNode;

    @Schema(description = "该循环区域内的节点 LiteFlow 节点 id（不含取数节点）")
    private List<String> memberElIds = new ArrayList<>();

    public List<String> getMemberElIds() {
        return memberElIds == null ? memberElIds = new ArrayList<>() : memberElIds;
    }
}
