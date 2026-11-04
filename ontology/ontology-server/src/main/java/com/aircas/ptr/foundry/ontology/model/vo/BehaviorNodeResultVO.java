package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorNodeKindEnum;
import com.aircas.ptr.foundry.ontology.model.enums.BehaviorStateEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 行为树单轮执行的节点结果，与定义树同形，便于前端按节点定位问题。
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树节点执行结果")
public class BehaviorNodeResultVO {

    @Schema(name = "nodeId", description = "节点标识", example = "node_1")
    private String nodeId;

    @Schema(name = "kind", description = "节点类型", example = "SEQUENCE")
    private BehaviorNodeKindEnum kind;

    @Schema(name = "displayName", description = "节点显示名称")
    private String displayName;

    @Schema(name = "state", description = "本节点结果：SUCCESS / FAILURE；单轮执行不产生 RUNNING", example = "SUCCESS")
    private BehaviorStateEnum state;

    @Schema(name = "skipped", description = "是否因未启用或跳过条件成立而跳过；跳过的节点按成功参与父节点汇总")
    private boolean skipped;

    @Schema(name = "message", description = "结果说明，失败原因或落库动作都写在这里")
    private String message;

    @Schema(name = "children", description = "子节点结果")
    @Builder.Default
    private List<BehaviorNodeResultVO> children = new ArrayList<>();
}
