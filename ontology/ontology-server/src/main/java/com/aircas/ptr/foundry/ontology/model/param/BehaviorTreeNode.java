package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.BehaviorNodeKindEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 行为树节点。
 * <p>每个节点固定三项公共配置 enabled/skipIf/whileCondition，其余按 kind 走强类型 config，
 * config 里出现该 kind 不认识的字段直接报错，不允许自由字段。
 * whileCondition 在单轮里作为进入条件，循环节点还会在每轮执行前重新求值。</p>
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树节点")
public class BehaviorTreeNode {

    @Schema(name = "id", description = "节点标识，树内唯一", example = "node_1")
    private String id;

    @Schema(name = "kind", description = "节点类型", example = "SEQUENCE")
    private BehaviorNodeKindEnum kind;

    @Schema(name = "displayName", description = "节点显示名称")
    private String displayName;

    @Schema(name = "enabled", description = "是否启用，为空按启用处理", example = "true")
    private Boolean enabled;

    @Schema(name = "skipIf", description = "跳过条件，SpEL 表达式；空表达式表示不启用，运行时按黑板上下文求值")
    private String skipIf;

    @Schema(name = "whileCondition", description = "持续执行条件，SpEL 表达式；作为节点进入条件，循环节点每轮重新求值")
    private String whileCondition;

    @Schema(name = "config", description = "类型专属配置，字段集合按 kind 白名单校验")
    private Object config;

    @Schema(name = "children", description = "子节点列表；容器节点多个、装饰器节点恰好一个、叶子节点为空")
    private List<BehaviorTreeNode> children = new ArrayList<>();

    public List<BehaviorTreeNode> getChildren() {
        return children == null ? new ArrayList<>() : children;
    }

    public boolean isEnabled() {
        return enabled == null || enabled;
    }
}
