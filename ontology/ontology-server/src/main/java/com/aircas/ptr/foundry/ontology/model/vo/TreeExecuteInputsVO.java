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
 * 行为树执行入参要求：按行为节点逐个描述要传的数据，调用方据此补齐 inputs、nodeExecutes 后再调 execute / test / executeBatch。
 * <p>黑板键是树级条件变量，不属于节点：来源是固定值的用黑板项自己的取值，外部输入可在 inputs 里覆盖，
 * 对象属性按库里的当前值刷新，所属对象在 blackboardInstances 里单独列出。</p>
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树执行入参要求")
public class TreeExecuteInputsVO {

    @Schema(name = "entityPrimaryKeyRequired", description = "执行请求是否需要实例主键：任一节点需要实例数据，"
            + "或有黑板键的来源是对象属性", example = "true")
    private boolean entityPrimaryKeyRequired;

    @Schema(name = "nodes", description = "行为节点逐个列出：每个节点要用的实例、外部入参和写回位置，按节点在 nodeExecutes 里给")
    private List<TreeExecuteNodeVO> nodes = new ArrayList<>();

    @Schema(name = "blackboardInstances", description = "黑板里来源是对象属性的键要按对象选实例；实例由执行请求的 entityPrimaryKeys 给。"
            + "属性不在主属性分组的条目还带 storageGroups，要按 inputDataPrimaryKeys[对象标识][分组] 再选该分组的一行")
    private List<TreeBlackboardInstanceVO> blackboardInstances = new ArrayList<>();

    public List<TreeExecuteNodeVO> getNodes() {
        return nodes == null ? new ArrayList<>() : nodes;
    }

    public List<TreeBlackboardInstanceVO> getBlackboardInstances() {
        return blackboardInstances == null ? new ArrayList<>() : blackboardInstances;
    }
}
