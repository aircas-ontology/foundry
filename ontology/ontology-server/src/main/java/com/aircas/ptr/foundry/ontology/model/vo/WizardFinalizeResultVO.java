package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 向导完成 - 落库结果
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@Schema(description = "向导完成-落库结果")
public class WizardFinalizeResultVO {

    @Schema(name = "spaceId", description = "空间id", example = "1")
    private Integer spaceId;

    @Schema(name = "ontologyUniqueIdentifier", description = "新创建本体的唯一标识", example = "8039c5f9-5579-4ee4-ba94-b2f25f785dd6")
    private String ontologyUniqueIdentifier;

    @Schema(name = "apiName", description = "本体标识", example = "arleigh_burke_destroyer")
    private String apiName;

    @Schema(name = "displayName", description = "本体名称", example = "阿利·伯克级驱逐舰")
    private String displayName;

    @Schema(name = "propertyCount", description = "成功创建的属性数量", example = "12")
    private Integer propertyCount;

    @Schema(name = "relationCount", description = "成功创建的关系数量", example = "3")
    private Integer relationCount;
}
