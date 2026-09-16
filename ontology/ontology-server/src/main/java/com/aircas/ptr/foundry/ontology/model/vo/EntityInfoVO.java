package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Accessors(chain = true)
@Schema(description = "实体信息数据")
public class EntityInfoVO {

    @Schema(name = "ontologyUniqueIdentifier",description = "本体id")
    private String ontologyUniqueIdentifier;

    @Schema(name = "ontologyName",description = "本体名称")
    private String ontologyName;

    @Schema(name = "primaryKey",description = "实体主键id")
    private Object primaryKey;

    @Schema(name = "displayName",description = "实体显示名称")
    private String displayName;

    @Schema(name = "properties",description = "实体属性值")
    private List<EntityPropertyVO> properties;
}
