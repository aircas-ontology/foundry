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
 * 行为树里需要实例数据的地方，按对象列出：
 * 属性值/实例关系行为、入参取对象属性或出参写属性的行为，以及来源是对象属性的黑板键。
 * <p>实例一律由执行请求给：全树共用一个传 entityPrimaryKey，涉及多个对象类型时按对象传 entityPrimaryKeys。</p>
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "行为树实例要求")
public class TreeInstanceRequirementVO {

    @Schema(name = "actionApi", description = "需要的实例的行为 api；黑板属性来源的实例要求为空", example = "act.satellite.track")
    private String actionApi;

    @Schema(name = "actionDisplayName", description = "行为显示名称；黑板属性来源的实例要求为空", example = "卫星跟踪")
    private String actionDisplayName;

    @Schema(name = "objectIdentifier", description = "要在哪个对象类型下选实例；同一行为涉及多个对象类型时每个对象各一条",
            example = "satellite")
    private String objectIdentifier;

    @Schema(name = "nodeIds", description = "绑定该行为、需要实例的节点标识；黑板属性来源的实例要求为空")
    private List<String> nodeIds = new ArrayList<>();

    @Schema(name = "linkEntityPrimaryKeyRequired", description = "是否需要关系对端主键", example = "false")
    private boolean linkEntityPrimaryKeyRequired;

    @Schema(name = "linkObjectIdentifier",
            description = "关系对端对象的标识：需要关系对端主键时有值，调用方按这个对象选实例、放进 "
                    + "entityPrimaryKeys 即可作为对端；自关系（对端就是本对象）不返回该字段，仍需显式传 linkEntityPrimaryKey",
            example = "groundStation")
    private String linkObjectIdentifier;

    @Schema(name = "blackboardKeys", description = "该对象实例是给这些黑板键读属性用的：非空表示来源是对象属性的黑板键；"
            + "行为来源的实例要求为空", example = "[\"speed\"]")
    private List<String> blackboardKeys = new ArrayList<>();

    public List<String> getNodeIds() {
        return nodeIds == null ? new ArrayList<>() : nodeIds;
    }

    public List<String> getBlackboardKeys() {
        return blackboardKeys == null ? new ArrayList<>() : blackboardKeys;
    }
}
