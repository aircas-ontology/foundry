package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

/**
 * 行为节点要用到的一个对象实例：属性值/实例关系行为、入参取对象属性或出参写属性的行为都要实例。
 * <p>实例一律由执行请求给：节点只涉及一个对象时用 nodeExecutes[节点标识].entityPrimaryKey，
 * 涉及多个对象时按对象传 nodeExecutes[节点标识].entityPrimaryKeys；
 * 行为绑定关系时还要按 entityPrimaryKeys.from、entityPrimaryKeys.to 成对传关系两端，与单行为测试的口径一致。
 * 是否必须给由节点上的 entityPrimaryKeyRequired 决定：基础函数行为列对象但每条都可选，不选按该对象全量查。</p>
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为节点实例要求")
public class TreeInstanceRequirementVO {

    @Schema(name = "objectIdentifier", description = "要在哪个对象类型下选实例；同一行为涉及多个对象类型时每个对象各一条",
            example = "satellite")
    private String objectIdentifier;

    @Schema(name = "linkEntityPrimaryKeyRequired", description = "是否需要关系两端实例：行为绑定关系时有值，"
            + "按关系两端传 entityPrimaryKeys.from / entityPrimaryKeys.to（成对必传）", example = "false")
    private boolean linkEntityPrimaryKeyRequired;

    @Schema(name = "linkFromObjectIdentifier",
            description = "关系 From 端对象的标识：需要关系两端实例时有值，调用方按这个对象选实例、放进 "
                    + "entityPrimaryKeys.from；自关系时与 to 是同一个对象，仍要选两个不同实例",
            example = "satellite")
    private String linkFromObjectIdentifier;

    @Schema(name = "linkToObjectIdentifier",
            description = "关系 To 端对象的标识：需要关系两端实例时有值，调用方按这个对象选实例、放进 "
                    + "entityPrimaryKeys.to",
            example = "groundStation")
    private String linkToObjectIdentifier;

    @Schema(name = "selfRelation", description = "关系两端是同一个对象类型；此时 from、to 仍要传两个不同实例",
            example = "false")
    private boolean selfRelation;

    @Schema(name = "linkObjectIdentifier",
            description = "关系对端对象的标识：需要关系两端实例时有值，调用方按这个对象选实例、放进 "
                    + "entityPrimaryKeys 即可作为对端；自关系（对端就是本对象）不返回该字段，要按 from、to 传两个不同实例",
            example = "groundStation")
    private String linkObjectIdentifier;
}
