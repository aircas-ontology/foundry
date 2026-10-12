package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.util.Map;

/**
 * 子空间创建结果。
 */
@Data
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "子空间创建结果")
public class OntologySubspaceCreateVO {

    @Schema(name = "spaceId", description = "新建子空间 id")
    private Integer spaceId;

    @Schema(name = "ontologyMapping", description = "源本体 uniqueIdentifier -> 新本体 uniqueIdentifier 映射")
    private Map<String, String> ontologyMapping;

    @Schema(name = "linkMapping", description = "源关系 uniqueIdentifier -> 新关系 uniqueIdentifier 映射")
    private Map<String, String> linkMapping;
}
