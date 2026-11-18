package com.aircas.ptr.foundry.ontology.model.vo;

import com.aircas.ptr.foundry.ontology.model.enums.AggFuncEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "基础函数行为单个对象的查询结果")
public class ActionBasicFunctionObjectResultVO {

    @Schema(name = "ontologyUniqueIdentifier", description = "该条结果对应的对象id", example = "3f2a9c1e-7b4d-4a86-9e21-5c8d1f0a6b34")
    private String ontologyUniqueIdentifier;

    @Schema(name = "ontologyDisplayName", description = "对象名称", example = "卫星")
    private String ontologyDisplayName;

    @Schema(name = "aggFunc", description = "聚合函数；查询时为空", example = "COUNT")
    private AggFuncEnum aggFunc;

    @Schema(name = "targetField", description = "聚合目标属性的 apiName；查询时为空", example = "speed")
    private String targetField;

    @Schema(name = "value", description = "聚合值；没有命中数据时为 0 或空，按聚合函数本身的返回为准。查询时为空", example = "12")
    private Object value;

    @Schema(name = "records", description = "查询到的数据行：一行是若干属性的取值。聚合时为空")
    private List<List<EntityPropertyGenericQueryVO>> records;

    @Schema(name = "total", description = "查询到的总行数（不是页数）。聚合时为空", example = "120")
    private Long total;
}
