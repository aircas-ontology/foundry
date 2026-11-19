package com.aircas.ptr.foundry.ontology.model.param;

import com.aircas.ptr.foundry.ontology.model.enums.AggFuncEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * 跨对象统一聚合请求：多个对象的同一个属性合成一批值，只做一次聚合函数。
 * <p>与 {@link EntityPropertyGenericQueryParam} 的聚合（一次一个对象，各算各的值）不同：
 * AVG、COUNT 这类结果不能拿各对象的值再合并，必须先把行放在一起才算得出全局值。</p>
 */
@Schema(description = "跨对象统一聚合查询请求")
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class EntityUnionAggregateParam {

    @Schema(name = "aggFunc", description = "聚合函数：SUM/COUNT/AVG/MAX/MIN/DISTINCT", example = "AVG")
    private AggFuncEnum aggFunc;

    @Schema(name = "propertyApiName", description = "参与聚合的属性 apiName，每个来源对象上都要有这个属性且已关联数据源字段", example = "speed")
    private String propertyApiName;

    @Schema(name = "sources", description = "参与聚合的对象，至少一个；每个对象的行各自查出来再合批", example = "[{\"ontologyIdentifier\":\"3f2a9c1e-7b4d-4a86-9e21-5c8d1f0a6b34\"}]")
    private List<Source> sources;

    /** 一个对象贡献进这批值的行范围：对象 + 本次限定的实例与条件 */
    @Schema(description = "统一聚合的单个来源对象")
    @Data
    @SuperBuilder
    @Accessors(chain = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Source {

        @Schema(name = "ontologyIdentifier", description = "对象id", example = "3f2a9c1e-7b4d-4a86-9e21-5c8d1f0a6b34")
        private String ontologyIdentifier;

        @Schema(name = "entityPrimaryKey", description = "实例主键，非空时该对象的行限定到该实例；不传=该对象全量参与聚合", example = "1")
        private Object entityPrimaryKey;

        @Schema(name = "filters", description = "该对象的过滤树，与实例主键是并列的 AND 条件；不传=不加条件")
        private FilterGroupParam filters;
    }
}
