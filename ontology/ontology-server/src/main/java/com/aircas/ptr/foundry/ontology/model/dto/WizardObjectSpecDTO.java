package com.aircas.ptr.foundry.ontology.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 向导流程中的"本体对象规格"
 * <p>
 * 双重身份：
 * 1. 步骤 3 (build-object) 的响应体
 * 2. 步骤 4 / 5 请求体里的 objectSpec 字段（前端可能修改后回传）
 * <p>
 * reasoning 字段是"构建依据"，前端只读，但会原样回传给后端作为下一步的上下文。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "本体对象规格（向导步骤3输出 / 步骤4-5输入）")
public class WizardObjectSpecDTO {

    @Schema(name = "name", description = "对象名称（中文显示名）", example = "阿利·伯克级驱逐舰")
    private String name;

    @Schema(name = "apiName", description = "对象标识（英文小写下划线）", example = "arleigh_burke_destroyer")
    private String apiName;

    @Schema(name = "description", description = "对象描述")
    private String description;

    @Schema(name = "categoryId", description = "分类id，从当前空间的 ontology_category 中选择；无合适分类时为 null", example = "5")
    private Integer categoryId;

    @Schema(name = "categoryName", description = "分类名称（后端根据 categoryId 回填，前端只读）", example = "舰船")
    private String categoryName;

    @Schema(name = "categoryPath", description = "分类全路径（后端根据 categoryId 回填，前端只读）", example = "武器装备/舰船")
    private String categoryPath;

    @Schema(name = "reasoning", description = "构建依据（前端只读，回传时保留原值）")
    private String reasoning;
}
