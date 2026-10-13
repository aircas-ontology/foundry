package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

/**
 * 函数编排连线。
 * <p>sourceParameterId/targetParameterId 为端口标识；{@code __flow_in__}/{@code __flow_out__}
 * 表示纯控制流连线，true/false 表示分支节点的命中出口。</p>
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "函数编排连线")
public class CompositeEdge {

    @Schema(name = "id", description = "连线唯一标识", example = "edge_1")
    private String id;

    @Schema(name = "sourceNodeId", description = "源节点标识", example = "node_1")
    private String sourceNodeId;

    @Schema(name = "sourceParameterId", description = "源节点输出端口标识", example = "__flow_out__")
    private String sourceParameterId;

    @Schema(name = "targetNodeId", description = "目标节点标识", example = "node_2")
    private String targetNodeId;

    @Schema(name = "targetParameterId", description = "目标节点输入端口标识", example = "__flow_in__")
    private String targetParameterId;
}