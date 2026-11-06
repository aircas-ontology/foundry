package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorStateEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

/**
 * 行为树批量执行中单条实例的结果。
 * <p>默认只回传失败的实例，带上黑板快照与节点结果，便于按节点定位是哪一步出的问题。</p>
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树批量执行的单实例结果")
public class TreeBatchInstanceResultVO {

    @Schema(name = "entityPrimaryKey", description = "实例主键", example = "1")
    private Object entityPrimaryKey;

    @Schema(name = "state", description = "整棵树的根节点结果", example = "SUCCESS")
    private BehaviorStateEnum state;

    @Schema(name = "message", description = "失败原因，取第一个失败节点带说明的信息")
    private String message;

    @Schema(name = "blackboard", description = "执行结束时的黑板快照")
    private Map<String, Object> blackboard;

    @Schema(name = "nodes", description = "根节点执行结果，子节点逐级嵌套")
    private List<BehaviorNodeResultVO> nodes;
}
