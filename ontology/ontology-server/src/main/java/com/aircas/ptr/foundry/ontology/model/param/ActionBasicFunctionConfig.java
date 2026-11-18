package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.AggFuncEnum;
import com.aircas.ptr.foundry.ontology.model.enums.BasicFunctionModeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * 基础函数行为（subjectType=BASIC_FUNCTION）的查询定义，序列化为 JSON 存入 ontology_action.basic_function_config。
 * <p>
 * 与基础查询算子的配置同形，但字段引用的是真实属性 apiName，不再有变量名与变量绑定那一层：
 * 单对象时前端从该对象的属性里选，多对象时手输一个 apiName、由创建校验确认每个对象都有这个属性。
 * 多对象只能聚合，且这些对象必须属于同一个对象分类；查询只作用于一个对象。
 */
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "基础函数行为配置")
public class ActionBasicFunctionConfig {

    @Schema(name = "ontologyIdentifiers", description = "查询的对象id 列表，至少一个；多于一个时必须是同一对象分类下的对象，且只能做聚合",
            example = "3f2a9c1e-7b4d-4a86-9e21-5c8d1f0a6b34")
    private List<String> ontologyIdentifiers;

    @Schema(name = "mode", description = "操作方式：AGGREGATE 聚合 / QUERY 查询", example = "AGGREGATE")
    private BasicFunctionModeEnum mode;

    @Schema(name = "aggFunc", description = "聚合函数：SUM/COUNT/AVG/MAX/MIN/DISTINCT；mode=AGGREGATE 时必填", example = "COUNT")
    private AggFuncEnum aggFunc;

    @Schema(name = "targetField", description = "聚合目标属性的 apiName；mode=AGGREGATE 时必填。"
            + "多对象时该属性必须在每个选中的对象上都存在", example = "speed")
    private String targetField;

    @Schema(name = "selectFields", description = "查询字段属性的 apiName 列表；mode=QUERY 时必填，至少一个。"
            + "字段落在子表（一对多存储分组）时结果按子表行展开", example = "[\"name\",\"speed\"]")
    private List<String> selectFields;

    @Schema(name = "filters", description = "过滤条件（嵌套过滤树，propertyApiName 写真实属性 apiName）；不传则不加条件。"
            + "条件里的属性同样必须在每个选中的对象上都存在")
    private FilterGroupParam filters;
}
