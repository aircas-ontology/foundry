package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Schema(description = "行为测试/执行时某个对象在某一个方向上的数据位置：选哪个实例、每个存储分组取/写哪一行。"
        + "一个值就是一组完整的数据位置；LIST 入参的每个元素位置还可以各自指定对象，"
        + "一份列表可以由不同对象、不同实例的行拼成")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ActionInstanceTargetParam {

    @Schema(name = "ontologyIdentifier", description = "该位置读哪个对象的数据：只在按元素给位置的入参方向用，"
            + "一份列表的绑定跨多个对象时必填（同一个实例主键在不同对象下指的不是同一条数据）；"
            + "绑定只涉及一个对象时不用传。按子级字段（父参数名.字段路径）给位置时不要传，"
            + "读哪个对象由创建行为时的绑定行决定，传了报错",
            example = "375e4a851e4944fcbf4d77ebd6d67ac3")
    private String ontologyIdentifier;

    @Schema(name = "entityPrimaryKey", description = "该位置用的实例主键；不传时用 entityPrimaryKeys 里该对象的实例；"
            + "行为测试的入参方向不要在这里传数组，多组取值请给外层数组放多个数据位置",
            example = "1")
    private Object entityPrimaryKey;

    @Schema(name = "dataPrimaryKeys", description = "存储分组 -> 子表行主键。入参方向只读该行，出参方向写到该行不再新增；"
            + "不传的分组沿用原口径（入参取最新一行，出参新增一行）；main 分组忽略；"
            + "非 LIST 入参不要在这里传数组，多组取值请给外层数组放多个数据位置；"
            + "LIST 入参的一个元素就是列表的一项，一个元素只给一行；按顶层入参名给位置时，"
            + "把行主键写成数组会按数组顺序展开成多个元素（各分组行数必须一致，一个下标就是一个列表元素）；"
            + "按绑定行字段名给位置时外层数组的每个元素就是一个列表元素，行主键不要再套数组",
            example = "{\"weapon_load\":11}")
    private Map<String, Object> dataPrimaryKeys;

    /** 调用方是否按方向指定了实例主键；只给分组行主键不算，那条链路的行为与改造前一致 */
    public static boolean hasEntityPrimaryKey(Map<String, ActionInstanceTargetParam> targets) {
        return targets != null && targets.values().stream()
                .anyMatch(target -> target != null && target.getEntityPrimaryKey() != null);
    }
}
