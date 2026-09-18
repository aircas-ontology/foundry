package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.controller.validator.SpaceIdVerify;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

/**
 * 本体关系发现请求参数
 * <p>
 * 场景：新本体尚未入库时，前端把用户填的名称/描述传过来，
 * 后端在指定空间下用 LLM 判断它与已有本体是否存在关系。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
@Accessors(chain = true)
@Schema(description = "本体关系发现请求参数")
public class OntologyRelationDiscoveryParam {

    @Schema(name = "spaceId", description = "本体空间id", required = true, example = "1")
    @NotNull(message = "空间id不能为空")
    @SpaceIdVerify
    private Integer spaceId;

    @Schema(name = "newOntologyName", description = "新本体显示名称（尚未入库）",
            required = true, example = "阿利·伯克级驱逐舰")
    @NotBlank(message = "新本体名称不能为空")
    @Size(max = 128, message = "新本体名称过长，最多 128 字符")
    private String newOntologyName;

    @Schema(name = "newOntologyDescription", description = "新本体描述（可选，但强烈建议填写，会显著提升匹配准确率）",
            example = "美国海军现役主力驱逐舰，配备宙斯盾系统与垂直发射装置")
    @Size(max = 2000, message = "新本体描述过长，最多 2000 字符")
    private String newOntologyDescription;
}
