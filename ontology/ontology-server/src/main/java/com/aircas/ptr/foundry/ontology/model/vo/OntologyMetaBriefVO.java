package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 本体对象简要信息（按本地主键 id 查询）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@Schema(description = "本体对象简要信息")
public class OntologyMetaBriefVO {

    /**
     * 本地对象id（ontology_meta 主键）
     */
    @Schema(name = "id", description = "本地对象id", example = "1")
    private Long id;

    /**
     * 对象名称
     */
    @Schema(name = "displayName", description = "对象名称", example = "飞机")
    private String displayName;

    /**
     * 空间名称
     */
    @Schema(name = "spaceName", description = "所属本体空间名称", example = "航空领域")
    private String spaceName;

    /**
     * 对象的唯一标识
     */
    @Schema(name = "uniqueIdentifier", description = "对象唯一标识", example = "abc123")
    private String uniqueIdentifier;
}
