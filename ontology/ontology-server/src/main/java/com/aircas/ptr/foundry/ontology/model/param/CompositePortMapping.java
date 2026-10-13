package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

/**
 * 函数编排对外参数与节点端口的映射关系。
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "函数编排端口映射")
public class CompositePortMapping {

    @Schema(name = "id", description = "映射唯一标识", example = "map_1")
    private String id;

    @Schema(name = "externalParameterId", description = "编排对外参数标识", example = "p_startTime")
    private String externalParameterId;

    @Schema(name = "nodeId", description = "映射到的节点标识", example = "node_1")
    private String nodeId;

    @Schema(name = "nodeParameterId", description = "映射到的节点端口标识", example = "startTime")
    private String nodeParameterId;
}