package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * Switch Case 节点配置。
 * <p>switchOnPath 为上游出参名（也是节点输入端口名），cases 中的 id 与画布控制边 sourceParameterId 对齐，
 * value 为手填匹配值；未命中任何 case 时走 default 出口。</p>
 */
@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "函数编排 Switch Case 节点配置")
public class CompositeSwitchConfig {

    @Schema(name = "switchOnPath", description = "开关取值路径（上游出参名/节点输入端口名）", example = "value")
    private String switchOnPath;

    @Schema(name = "cases", description = "case 列表；id 与控制边 sourceParameterId 对齐，value 为匹配值")
    private List<CaseItem> cases;

    /**
     * Switch Case 单条分支定义。
     */
    @Data
    @SuperBuilder
    @AllArgsConstructor
    @NoArgsConstructor
    @Schema(description = "Switch Case 分支定义")
    public static class CaseItem {

        @Schema(name = "id", description = "case 唯一标识，与控制边 sourceParameterId 对齐", example = "case_1")
        private String id;

        @Schema(name = "value", description = "手填匹配值", example = "A")
        private String value;
    }
}
