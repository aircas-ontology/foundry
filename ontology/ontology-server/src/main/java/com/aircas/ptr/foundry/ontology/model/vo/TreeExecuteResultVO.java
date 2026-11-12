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
 * 行为树单轮执行结果。
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树执行结果")
public class TreeExecuteResultVO {

    @Schema(name = "treeApi", description = "行为树api", example = "tree.satellite.watch")
    private String treeApi;

    @Schema(name = "state", description = "整棵树的根节点结果", example = "SUCCESS")
    private BehaviorStateEnum state;

    @Schema(name = "blackboard", description = "执行结束时的黑板快照：变量默认值被执行入参覆盖后的结果，"
            + "绑对象属性的键是最后一次条件求值前从库里读到的值；行为不写黑板")
    private Map<String, Object> blackboard;

    @Schema(name = "nodes", description = "根节点执行结果，子节点逐级嵌套")
    private List<BehaviorNodeResultVO> nodes;
}
