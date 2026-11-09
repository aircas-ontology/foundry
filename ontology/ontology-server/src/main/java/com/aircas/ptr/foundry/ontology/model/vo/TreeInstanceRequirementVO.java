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
 * 行为树里需要实例数据的节点：属性值/实例关系行为、入参取对象属性或出参写属性的行为。
 * <p>实例主键默认取执行请求里的 entityPrimaryKey；节点配置了 entityPrimaryKeyKey 时改用该黑板键的值，
 * 这样不同对象类型的节点可以各用各的实例。</p>
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树实例要求")
public class TreeInstanceRequirementVO {

    @Schema(name = "actionApi", description = "需要的实例的行为 api", example = "act.satellite.track")
    private String actionApi;

    @Schema(name = "actionDisplayName", description = "行为显示名称", example = "卫星跟踪")
    private String actionDisplayName;

    @Schema(name = "objectIdentifier", description = "要在哪个对象类型下选实例；同一行为涉及多个对象类型时每个对象各一条", example = "satellite")
    private String objectIdentifier;

    @Schema(name = "nodeIds", description = "绑定该行为、需要实例且用同一取值来源的节点标识")
    private List<String> nodeIds = new ArrayList<>();

    @Schema(name = "entityPrimaryKeyKey", description = "节点配置的实例主键黑板键；为空表示用执行请求里的 entityPrimaryKey")
    private String entityPrimaryKeyKey;

    @Schema(name = "entityPrimaryKeyRequired", description = "是否需要执行请求里的 entityPrimaryKey：该实例要求没有配置节点级键时为 true", example = "true")
    private boolean entityPrimaryKeyRequired;

    @Schema(name = "linkEntityPrimaryKeyRequired", description = "是否需要关系对端主键，节点可用 linkEntityPrimaryKeyKey 从黑板取", example = "false")
    private boolean linkEntityPrimaryKeyRequired;

    @Schema(name = "linkObjectIdentifier",
            description = "关系对端对象的标识：需要关系对端主键且节点没配对端键时有值，调用方按这个对象选实例、放进 "
                    + "entityPrimaryKeys 即可作为对端；自关系（对端就是本对象）不返回该字段，仍需显式传 linkEntityPrimaryKey",
            example = "groundStation")
    private String linkObjectIdentifier;

    public List<String> getNodeIds() {
        return nodeIds == null ? new ArrayList<>() : nodeIds;
    }
}
