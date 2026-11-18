package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 行为节点上一个入参的取值来源。
 * <p>入参名是这个绑定在 inputBindings 里的键，值里只填来源：要么 blackboardKey 取黑板变量，
 * 要么 nodeId + output 取上游行为节点的出参，两种只能选一种。都没配的入参照旧按行为声明的来源取值。</p>
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树行为节点单个入参的取值来源")
public class BehaviorInputBinding {

    @Schema(name = "blackboardKey", description = "取本树声明的哪个黑板键的当前值；与 nodeId、output 二选一",
            example = "patrolLevel")
    private String blackboardKey;

    @Schema(name = "nodeId", description = "取上游行为节点出参时填那个节点的标识，要和 output 一起给；"
            + "只能引用同一顺序节点里排在前面、必然成功的行为节点", example = "node_calc")
    private String nodeId;

    @Schema(name = "output", description = "取上游节点的那条出参，写法与出参写回同一套："
            + "属性唯一标识 > 出参字段名 > 顶层算子出参名", example = "prop_orbit_altitude")
    private String output;
}
