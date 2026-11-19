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
 * 多对象只能聚合，且这些对象必须属于同一个对象分类；聚合是把各对象命中的行合成一批，只算一个统一聚合值
 * （不是每个对象各算一个，那样 AVG/COUNT 合不出全局值）。查询只作用于一个对象。
 * <p>{@link #writeBack} 配了才落库：不配就是纯查询，行为输出仍只是返回结果。</p>
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
            + "多对象时该属性必须在每个选中的对象上都存在，各对象命中的值合到一批里算同一个聚合值", example = "speed")
    private String targetField;

    @Schema(name = "selectFields", description = "查询字段属性的 apiName 列表；mode=QUERY 时必填，至少一个。"
            + "字段落在子表（一对多存储分组）时结果按子表行展开", example = "[\"name\",\"speed\"]")
    private List<String> selectFields;

    @Schema(name = "filters", description = "过滤条件（嵌套过滤树，propertyApiName 写真实属性 apiName）；不传则不加条件。"
            + "条件里的属性同样必须在每个选中的对象上都存在")
    private FilterGroupParam filters;

    @Schema(name = "writeBack", description = "结果回写配置；不传就是只查不落库。"
            + "查询方式把每个查询字段按行追加到目标对象的某个子表分组，聚合方式把聚合值写进目标对象主表的一个属性")
    private WriteBack writeBack;

    /**
     * 结果回写目标：一次执行只有一个落点对象，字段绑定只能落在该对象的一个存储分组里。
     * <p>查询方式是往子表分组追加行（一条结果行一行数据），必须指明分组；聚合方式是改目标实例主表上已有的属性，
     * 分组固定 main，不需要配。两种方式的回写实例都由执行请求按对象传，创建时只建立绑定关系。</p>
     */
    @Data
    @SuperBuilder
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    @Schema(description = "基础函数行为的结果回写配置")
    public static class WriteBack {

        @Schema(name = "targetOntologyIdentifier", description = "回写目标对象id；可以不是查询的对象，但必须和行为同属一个对象空间",
                example = "3f2a9c1e-7b4d-48ce-b029-8ab05dae780c")
        private String targetOntologyIdentifier;

        @Schema(name = "targetStorageGroup", description = "回写目标存储分组（分组名即物理表名）；查询方式必填且不能是 main，"
                + "聚合方式不传（固定写 main 主表）", example = "track")
        private String targetStorageGroup;

        @Schema(name = "bindings", description = "字段绑定；查询方式每个要落库的查询字段一条，聚合方式每个要写的属性一条")
        private List<FieldBinding> bindings;
    }

    /**
     * 一条回写绑定：结果里的哪个值 → 目标对象的哪个属性。
     * <p>目标属性存唯一标识而不是 apiName，属性改名不影响绑定；apiName 只在真正写库时按属性查回来用。</p>
     */
    @Data
    @SuperBuilder
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    @Schema(description = "基础函数行为结果回写的字段绑定")
    public static class FieldBinding {

        @Schema(name = "sourceField", description = "来源查询字段的属性 apiName；仅查询方式使用，"
                + "聚合方式不传（多对象也只有一个统一聚合值，没有来源对象可选）", example = "speed")
        private String sourceField;

        @Schema(name = "targetPropertyIdentifier", description = "回写目标属性的唯一标识；查询方式必须属于 targetStorageGroup，"
                + "聚合方式必须是目标对象 main 分组里的属性且不能是主键", example = "8c1f4b2a-3d6e-4f70-9a5b-21ce6d8f0477")
        private String targetPropertyIdentifier;
    }
}
