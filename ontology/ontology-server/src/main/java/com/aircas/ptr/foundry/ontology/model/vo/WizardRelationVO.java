package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.OntologyLinkTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 向导步骤 5 输出的关系条目
 * <p>
 * 方向约定：新本体（步骤 3 定义的对象）总是 source，已有本体总是 target。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Schema(description = "本体关系条目（向导步骤5输出）")
public class WizardRelationVO {

    @Schema(name = "name", description = "关系名称", example = "装备宙斯盾系统")
    private String name;

    @Schema(name = "sourceObjectName", description = "源对象名称（新本体）", example = "阿利·伯克级驱逐舰")
    private String sourceObjectName;

    @Schema(name = "sourceObjectApiName", description = "源对象标识（新本体）", example = "arleigh_burke_destroyer")
    private String sourceObjectApiName;

    @Schema(name = "targetUniqueIdentifier", description = "目标对象唯一标识（对应 ontology_meta.unique_identifier）")
    private String targetUniqueIdentifier;

    @Schema(name = "targetObjectName", description = "目标对象名称", example = "宙斯盾系统")
    private String targetObjectName;

    @Schema(name = "targetObjectApiName", description = "目标对象标识", example = "aegis_system")
    private String targetObjectApiName;

    @Schema(name = "targetObjectDescription", description = "目标对象描述")
    private String targetObjectDescription;

    @Schema(name = "type", description = "关系类型枚举", example = "COMPOSITION")
    private OntologyLinkTypeEnum type;

    @Schema(name = "typeName", description = "关系类型中文名", example = "组合关系")
    private String typeName;

    @Schema(name = "description", description = "关系说明")
    private String description;

    @Schema(name = "reasoning", description = "构建依据（前端只读）")
    private String reasoning;
}
