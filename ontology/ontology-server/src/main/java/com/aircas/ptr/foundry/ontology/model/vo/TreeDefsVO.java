package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 行为树编辑器目录：可用的节点类型 + 本空间内可绑定的行为。
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树节点目录")
public class TreeDefsVO {

    @Schema(name = "nodes", description = "节点类型清单，含子节点数量约束")
    private List<TreeControlNodeVO> nodes = new ArrayList<>();

    @Schema(name = "availableActions", description = "本空间内已发布、可作为行为节点绑定的行为")
    private List<TreeAvailableActionVO> availableActions = new ArrayList<>();

    public List<TreeControlNodeVO> getNodes() {
        return nodes == null ? new ArrayList<>() : nodes;
    }

    public List<TreeAvailableActionVO> getAvailableActions() {
        return availableActions == null ? new ArrayList<>() : availableActions;
    }
}
