package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * 行为树执行请求。
 * <p>实例主键可选：只有节点真正需要实例时才传——绑属性值/实例关系行为、入参取对象属性、黑板键绑了对象属性。
 * 基础算子这类按对象做条件/聚合查询的行为直接带对象标识执行，不需要具体实例。
 * 黑板键值由 inputs 覆盖变量默认值；一棵树里的行为可以涉及多个对象类型，未配置节点级实例键时
 * 都按这同一个主键定位，配置了 entityPrimaryKeyKey 的节点改用对应黑板键的值；
 * 多个对象类型各要实例时用 entityPrimaryKeys 按对象分开传。</p>
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
            description = "本次执行共用的实例主键；只有节点确实需要实例时才传（属性值/实例关系行为、入参取对象属性、黑板键绑对象属性），"
                    + "基础算子按对象查询不需要；行为节点配置了 entityPrimaryKeyKey 时该节点改用对应黑板键的值",
            example = "1")
    private Object entityPrimaryKey;

    @Schema(name = "entityPrimaryKeys",
            description = "按对象分别指定实例主键（对象标识 -> 实例主键）：树里多个对象类型都要实例、节点又没配实例键时用它，"
                    + "同一个对象优先于 entityPrimaryKey；没列到的对象仍回退 entityPrimaryKey",
            example = "{\"satellite\":1,\"groundStation\":2}")
    private Map<String, Object> entityPrimaryKeys;

    @Schema(name = "dataPrimaryKey",
            description = "分组表行主键：属性值-修改/删除用来定位具体一行；作为全树默认值，行为节点配置了黑板键时以节点为准",
            example = "123")
    private Object dataPrimaryKey;

    @Schema(name = "inputDataPrimaryKeys",
            description = "入参按行读取：对象id -> (存储分组 -> 行主键)，作为全树的默认值；"
                    + "应用行为的属性入参按它取行，节点配置了入参键时以节点为准；留空时取最新一行",
            example = "{\"3f2a9c1e-7b4d-4a86-9e21-5c8d1f0a6b34\":{\"weapon_load\":11}}")
    private Map<String, Map<String, Object>> inputDataPrimaryKeys;

    @Schema(name = "outputDataPrimaryKeys",
            description = "出参按行写回：对象id -> (存储分组 -> 行主键)，作为全树的默认值；"
                    + "应用行为的 WRITE_PROPERTY 出参写到该行，节点配置了出参键时以节点为准；留空时新增一行",
            example = "{\"a5e8c206-3f17-4d92-8b74-1c6f9d3e5028\":{\"duty_log\":12}}")
    private Map<String, Map<String, Object>> outputDataPrimaryKeys;

    @Schema(name = "linkEntityPrimaryKey",
            description = "实例关系行为的对端实体主键；作为全树默认值，行为节点配置了黑板键时以节点为准",
            example = "2")
    private Object linkEntityPrimaryKey;

    @Schema(name = "inputs", description = "黑板键的初始取值（黑板键名 -> 值），只允许覆盖已声明且未绑定对象属性的键")
    private Map<String, Object> inputs;
}
