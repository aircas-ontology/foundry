package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

/**
 * 转换节点配置。
 * <p>脚本以 Groovy 执行，脚本入参为上游汇聚的数据 Map，返回值写入当前节点输出端口。
 * 端口可通过脚本注释 {@code // @in name:type} 与 {@code // @out name:type} 声明。</p>
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "函数编排转换节点配置")
public class CompositeTransformConfig {

    @Schema(name = "language", description = "脚本语言，当前支持 groovy", example = "groovy")
    private String language;

    @Schema(name = "script", description = "转换脚本内容")
    private String script;
}