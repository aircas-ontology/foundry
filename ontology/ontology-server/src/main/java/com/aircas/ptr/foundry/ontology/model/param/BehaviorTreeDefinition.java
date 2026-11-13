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
 * <p>黑板与变量按用户口径存两份（详情分别返回更好查），两者都不是必填：树可以一个黑板键都不声明（只靠行为节点串起来）。
 * 变量只给外部输入来源的键配兜底默认值，给了就必须键名已声明、类型一致，保存期强校验；固定值的取值写在黑板项自己的 value 上，
 * 对象属性的取值每次条件求值按实例回读。树不指向任何对象类型：它绑定的是已经建好的行为，黑板只用来决定节点执行不执行，
 * 行为需要的外部入参由执行请求 nodeExecutes 按节点给，不和黑板键混用。</p>
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树定义")
public class BehaviorTreeDefinition {

    @Schema(name = "blackboard", description = "黑板项列表，定义条件求值用的全局变量键名；只调用行为、不做条件判断的树可以一个键都不声明")
    private List<BehaviorBlackboardItem> blackboard = new ArrayList<>();

    @Schema(name = "variables", description = "变量列表，只给外部输入来源的黑板键配兜底默认值；固定值的取值在黑板项的 value 上、"
            + "对象属性按实例回读，这两种来源都不配变量")
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
