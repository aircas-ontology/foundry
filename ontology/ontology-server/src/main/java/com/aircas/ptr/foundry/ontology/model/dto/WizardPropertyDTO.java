package com.aircas.ptr.foundry.ontology.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 向导流程中的"本体属性规格"
 * <p>
 * 双重身份：
 * 1. 步骤 4 (build-properties) 响应体的列表元素
 * 2. 步骤 5 请求体里的 properties 字段（前端可能修改后回传）
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "本体属性规格（向导步骤4输出 / 步骤5输入）")
public class WizardPropertyDTO {

    @Schema(name = "name", description = "属性名称（中文）", example = "舰名")
    private String name;

    @Schema(name = "summary", description = "属性摘要", example = "驱逐舰的名称")
    private String summary;

    @Schema(name = "field", description = "对应数据源字段名；无对应字段时为 null", example = "name")
    private String field;

    @Schema(name = "type", description = "属性类型，来自 OntologyDataTypeEnum", example = "String")
    private String type;

    @Schema(name = "reasoning", description = "构建依据（前端只读，回传时保留原值）")
    private String reasoning;
}
