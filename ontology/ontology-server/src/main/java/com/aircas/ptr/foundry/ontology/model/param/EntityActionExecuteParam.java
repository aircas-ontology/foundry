package com.aircas.ptr.foundry.ontology.model.param;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Schema(description = "实体行为执行请求")
public class EntityActionExecuteParam extends EntityQueryParam {

    @Schema(name = "ontologySpaceId", description = "对象空间id", example = "156")
    @NotNull(message = "ontologySpaceId is null")
    private Integer ontologySpaceId;

    @Schema(name = "actionApi", description = "actionApi", example = "compute")
    @NotBlank(message = "actionApi is null")
    private String actionApi;

    @Schema(name = "dataPrimaryKey",
            description = "存储分组行的数据主键；属性值行为的修改/删除需要它定位到具体一行，"
                    + "main 分组可不传（等同实体主键），绑算子的行为忽略该字段", example = "123")
    private Object dataPrimaryKey;

    @Schema(name = "entityPrimaryKeys", description = "各对象选中的实例主键：对象id -> 实例主键；"
            + "应用行为的入参/出参可能跨对象，映射涉及的对象都要提供；单对象行为可继续只传 entityPrimaryKey")
    private Map<String, Object> entityPrimaryKeys;

    /**
     * 入参方向按对象给的数据位置：对象id -> {实例主键, 存储分组 -> 行主键}。
     * <p>只有内部调用会填（单行为测试把行为树的树级/节点级默认行主键按对象传进来），
     * 对外执行请求不接受，进来时在 {@code EntityServiceImpl#executeAction} 被剥掉。</p>
     */
    @Schema(hidden = true)
    private Map<String, ActionInstanceTargetParam> inputEntities;

    /** 出参方向按对象给的数据位置，同 {@link #inputEntities}，对外执行请求不接受 */
    @Schema(hidden = true)
    private Map<String, ActionInstanceTargetParam> outputEntities;

    @Schema(name = "inputParams", description = "按算子入参指定的数据位置：入参名 -> {实例主键, 存储分组->行主键}。"
            + "同一对象的两个属性入参要读不同实例时用它；命中时优先于 entityPrimaryKeys，"
            + "只影响该入参的读取，不影响出参写回和关系落库；关系对端与不绑算子的行为不支持")
    private Map<String, ActionInstanceTargetParam> inputParams;

    @Schema(name = "outputParams", description = "按出参绑定指定的写回位置：key -> {实例主键, 存储分组->行主键}。"
            + "key 从高到低认属性唯一标识 > 该条绑定的出参字段名 > 该条绑定所属的顶层算子出参名；"
            + "同一算子出参的几个属性写到不同实例时按字段名或属性唯一标识逐条点名；"
            + "命中时优先于 entityPrimaryKeys，只影响该行写回；关系对端与不绑算子的行为不支持")
    private Map<String, ActionInstanceTargetParam> outputParams;

    @Schema(name = "linkedOntologyUniqueIdentifier", description = "关联对象id", example = "123")
    private String linkedOntologyUniqueIdentifier;

    @Schema(name = "linkedEntityPrimaryKey", description = "关联对象id下某实体主键值", example = "123")
    private Object linkedEntityPrimaryKey;
}
