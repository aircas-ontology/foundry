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
@Schema(description = "实体id查询结果")
public class EntityIdsQueryVO {

    @Schema(name = "ontologyUniqueIdentifier", description = "本体唯一标识", example = "abcd")
    private String ontologyUniqueIdentifier;

    @Schema(name = "entityList", description = "实体属性信息列表")
    private List<EntityInfoVO> entityList;
}
