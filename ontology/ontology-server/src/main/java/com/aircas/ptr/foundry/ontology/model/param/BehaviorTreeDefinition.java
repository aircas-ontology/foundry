package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 行为树定义，序列化为 JSON 存入 ontology_action.definition，仅 action_kind=TREE 有值。
 * <p>黑板与变量按用户口径存两份（详情分别返回更好查），两者的键名集合必须一致、类型必须一致，
 * 保存期强校验。树不指向任何对象类型：它绑定的是已经建好的行为，自己只读写黑板。</p>
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树定义")
public class BehaviorTreeDefinition {

    @Schema(name = "blackboard", description = "黑板项列表，定义全局变量的键名")
    private List<BehaviorBlackboardItem> blackboard = new ArrayList<>();

    @Schema(name = "variables", description = "变量列表，定义与黑板键名一一对应的全局变量的值")
    private List<BehaviorVariableItem> variables = new ArrayList<>();

    @Schema(name = "roots", description = "根节点列表，保存与发布时必须且只能有一个")
    private List<BehaviorTreeNode> roots = new ArrayList<>();

    public List<BehaviorBlackboardItem> getBlackboard() {
        return blackboard == null ? new ArrayList<>() : blackboard;
    }

    public List<BehaviorVariableItem> getVariables() {
        return variables == null ? new ArrayList<>() : variables;
    }

    public List<BehaviorTreeNode> getRoots() {
        return roots == null ? new ArrayList<>() : roots;
    }
}
