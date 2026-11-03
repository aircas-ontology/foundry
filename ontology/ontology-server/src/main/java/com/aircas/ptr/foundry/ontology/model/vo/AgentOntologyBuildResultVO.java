package com.aircas.ptr.foundry.ontology.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 本体构建落库结果 VO。
 *
 * <p>「最终落库」成功后返回，告知 Agent 与前端新本体的唯一标识及落库统计，
 * 便于前端跳转到本体详情或画布。字段语义与画布落库返回
 * （{@code OntologySpaceCanvasCreateVO.OntologyItem}）对齐。</p>
 */
@Schema(description = "Agent 本体构建落库结果")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentOntologyBuildResultVO {

    @Schema(name = "uniqueIdentifier", description = "新建本体的唯一标识", example = "8039c5f9-5579-4ee4-ba94-b2f25f785dd6")
    private String uniqueIdentifier;

    @Schema(name = "displayName", description = "本体名称", example = "用户")
    private String displayName;

    @Schema(name = "apiName", description = "本体 api 名称", example = "user")
    private String apiName;

    @Schema(name = "spaceId", description = "所属空间 id", example = "1")
    private Integer spaceId;

    @Schema(name = "propertyCount", description = "成功落库的属性数量", example = "5")
    private Integer propertyCount;

    @Schema(name = "linkCount", description = "成功落库的关系数量", example = "1")
    private Integer linkCount;
}
