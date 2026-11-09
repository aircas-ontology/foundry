package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Schema(description = "行为测试/执行时某个对象在某一个方向上的数据位置：选哪个实例、每个存储分组取/写哪一行")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActionInstanceTargetParam {

    @Schema(name = "entityPrimaryKey", description = "该对象本次用的实例主键；不传时用 entityPrimaryKeys 里的实例",
            example = "1")
    private Object entityPrimaryKey;

    @Schema(name = "dataPrimaryKeys", description = "存储分组 -> 子表行主键。入参方向只读该行，出参方向写到该行不再新增；"
            + "不传的分组沿用原口径（入参取最新一行，出参新增一行）；main 分组忽略",
            example = "{\"weapon_load\":11}")
    private Map<String, Object> dataPrimaryKeys;

    /** 调用方是否按方向指定了实例主键；只给分组行主键不算，那条链路的行为与改造前一致 */
    public static boolean hasEntityPrimaryKey(Map<String, ActionInstanceTargetParam> targets) {
        return targets != null && targets.values().stream()
                .anyMatch(target -> target != null && target.getEntityPrimaryKey() != null);
    }
}
