package com.aircas.ptr.foundry.ontology.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 基础查询算子聚合结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "基础查询算子聚合结果")
public class BasicQueryResultVO {

    @Schema(name = "aggFunc", description = "聚合函数：SUM/COUNT/AVG/MAX/MIN", example = "SUM")
    private String aggFunc;

    @Schema(name = "targetProperty", description = "目标属性 apiName", example = "price")
    private String targetProperty;

    @Schema(name = "alias", description = "结果别名", example = "sum_price")
    private String alias;

    @Schema(name = "value", description = "聚合计算结果值", example = "100")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private Object value;
}
