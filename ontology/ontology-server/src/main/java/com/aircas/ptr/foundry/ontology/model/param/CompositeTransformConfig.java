package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

/**
 * 转换节点配置。
 * <p>支持两种模式：JSON 结构映射（默认）和脚本模式。
 * JSON 模式下通过 mappings 定义字段映射规则；脚本模式下通过 language 和 script 执行转换逻辑。</p>
 */
@Data
@SuperBuilder

@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "函数编排转换节点配置")
public class CompositeTransformConfig {

    @Schema(name = "mappingMode", description = "映射模式：json（JSON 结构映射）/ script（脚本模式）", example = "json")
    private String mappingMode;

    @Schema(name = "mappings", description = "JSON 映射规则列表")
    private List<Map<String, Object>> mappings;

    @Schema(name = "language", description = "脚本语言（script 模式时使用）", example = "groovy")
    private String language;

    @Schema(name = "script", description = "转换脚本内容（script 模式时使用）")
    private String script;
}