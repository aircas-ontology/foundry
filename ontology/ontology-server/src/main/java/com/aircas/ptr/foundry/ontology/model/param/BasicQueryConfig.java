package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.AggFuncEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

/**
 * 基础查询算子的查询模板配置，序列化为 JSON 存入 function.code 字段。
 * <p>targetProperty 和 filters 中的 propertyApiName 均为<b>变量名</b>，
 * 测试执行时由前端通过 variableBindings 映射到实际属性 apiName。</p>
 * <ul>
 *   <li>aggFunc 为 SUM/COUNT/AVG/MAX/MIN 时：需要聚合目标，targetProperty 前端未传则后端自动生成占位符</li>
 *   <li>aggFunc 为 null（query 模式）时：无聚合，targetProperty 可省略</li>
 * </ul>
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "基础查询算子配置")
public class BasicQueryConfig {

    @Schema(name = "aggFunc", description = "聚合类型：SUM/COUNT/AVG/MAX/MIN，不传则为 query 模式（无聚合）", example = "COUNT")
    private AggFuncEnum aggFunc;

    @Schema(name = "targetProperty", description = "目标占位符名（创建时不选实际字段，前端未传时后端自动生成为 target；执行时通过 variableBindings 映射到实际属性 apiName）", example = "amount")
    private String targetProperty;

    @Schema(name = "filters", description = "过滤条件（嵌套过滤树，propertyApiName 为变量名）")
    private FilterGroupParam filters;
}
