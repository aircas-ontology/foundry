package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * 行为树执行请求。
 * <p>行为需要的数据全部由这个请求给：实例主键、分组表行主键、关系对端，以及行为声明的外部输入参数。
 * 实例与数据位置可以全树共用一份；行为外部入参按节点给——两个节点绑同一个行为、各要不同值时用 nodeExecutes。
 * 实例主键可选：只有行为真正需要实例时才传——属性值/实例关系行为、入参取对象属性、出参写属性；
 * 基础算子这类按对象做条件/聚合查询的行为直接带对象标识执行，不需要具体实例。
 * 一棵树里的行为可以涉及多个对象类型，都按 entityPrimaryKey 定位同一个主键；
 * 多个对象类型各要实例时用 entityPrimaryKeys 按对象分开传。
 * 黑板变量只用来决定节点执行不执行，和执行入参是两套东西：inputs 只覆盖来源是外部输入的黑板键，
 * 固定值取声明值，对象属性来源的键在每次条件求值前按库里的当前值重读。</p>
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树执行请求")
public class TreeExecuteParam {

    @Schema(name = "ontologySpaceId", description = "行为树所属的对象空间id；行为api只在空间内唯一，同名树跨空间靠它区分",
            example = "156", required = true)
    @NotNull(message = "ontologySpaceId is null")
    private Integer ontologySpaceId;

    @Schema(name = "treeApi", description = "行为树api", example = "tree.satellite.watch", required = true)
    @NotBlank(message = "treeApi is empty")
    private String treeApi;

    @Schema(name = "entityPrimaryKey",
            description = "本次执行共用的实例主键；只有行为确实需要实例时才传（属性值/实例关系行为、入参取对象属性、出参写属性），"
                    + "基础算子按对象查询不需要；树里所有需要实例的节点都按这一个主键定位",
            example = "1")
    private Object entityPrimaryKey;

    @Schema(name = "entityPrimaryKeys",
            description = "按对象分别指定实例主键（对象标识 -> 实例主键）：树里多个对象类型都要实例时用它，"
                    + "同一个对象优先于 entityPrimaryKey；没列到的对象仍回退 entityPrimaryKey",
            example = "{\"satellite\":1,\"groundStation\":2}")
    private Map<String, Object> entityPrimaryKeys;

    @Schema(name = "dataPrimaryKey",
            description = "分组表行主键：属性值-修改/删除用来定位具体一行；全树共用",
            example = "123")
    private Object dataPrimaryKey;

    @Schema(name = "inputDataPrimaryKeys",
            description = "入参按行读取：对象id -> (存储分组 -> 行主键)，全树共用；"
                    + "应用行为的属性入参按它取行；留空时取最新一行",
            example = "{\"3f2a9c1e-7b4d-4a86-9e21-5c8d1f0a6b34\":{\"weapon_load\":11}}")
    private Map<String, Map<String, Object>> inputDataPrimaryKeys;

    @Schema(name = "outputDataPrimaryKeys",
            description = "出参按行写回：对象id -> (存储分组 -> 行主键)，全树共用；"
                    + "应用行为的 WRITE_PROPERTY 出参写到该行；留空时新增一行",
            example = "{\"a5e8c206-3f17-4d92-8b74-1c6f9d3e5028\":{\"duty_log\":12}}")
    private Map<String, Map<String, Object>> outputDataPrimaryKeys;

    @Schema(name = "inputParams",
            description = "按算子入参指定数据位置：入参名 -> 数据位置数组，与行为测试同一写法；"
                    + "一次执行每个入参只用一个数据位置，数组里放一个元素即可；"
                    + "同一对象的两个属性入参要读不同实例时用它；"
                    + "树里不同节点绑的行为参数名可能重名，按参数名的默认值会命中所有同名参数",
            example = "{\"max_mach\":[{\"entityPrimaryKey\":101,\"dataPrimaryKeys\":{\"duty_log\":11}}]}")
    private Map<String, List<ActionInstanceTargetParam>> inputParams;

    @Schema(name = "outputParams",
            description = "按出参绑定指定写回位置：key -> {实例主键, 存储分组 -> 行主键}，全树共用；"
                    + "key 从高到低认属性唯一标识 > 该条绑定的出参字段名 > 该条绑定所属的顶层算子出参名，与行为测试同一写法；"
                    + "同一算子出参的几个属性要写到不同实例时用它；"
                    + "按 key 的默认值会命中所有绑定的同名出参",
            example = "{\"name\":{\"entityPrimaryKey\":2},\"status\":{\"entityPrimaryKey\":3,\"dataPrimaryKeys\":{\"duty_log\":12}}}")
    private Map<String, ActionInstanceTargetParam> outputParams;

    @Schema(name = "linkEntityPrimaryKey",
            description = "实例关系行为的对端实体主键；全树共用。也可以不给它、改在 entityPrimaryKeys 里按对端对象传实例",
            example = "2")
    private Object linkEntityPrimaryKey;

    @Schema(name = "inputs", description = "黑板键的取值（键名 -> 值），只允许覆盖来源是外部输入的黑板键；"
            + "固定值来源以声明为准，对象属性来源每次条件求值按库里重读；行为外部入参不走这里，按节点传 nodeExecutes")
    private Map<String, Object> inputs;

    @Schema(name = "nodeExecutes", description = "按行为节点给的一整套数据（节点标识 -> 节点执行数据）："
            + "两个节点绑同一个行为、各要不同实例或不同参数时用它。实例与数据位置没给的沿用上面的全树默认值；"
            + "params 是行为外部入参，只认本节点的，没给的执行前按缺失报错。"
            + "键必须是树里行为节点的标识，节点级取值不会传给其他节点",
            example = "{\"node-a1b2\":{\"entityPrimaryKey\":2,\"params\":{\"ammoCount\":80}}}")
    private Map<String, TreeNodeExecuteParam> nodeExecutes;
}
