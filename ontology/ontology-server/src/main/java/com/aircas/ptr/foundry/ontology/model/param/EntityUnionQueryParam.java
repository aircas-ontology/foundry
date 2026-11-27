package com.aircas.ptr.foundry.ontology.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

/**
 * 跨对象合并查询请求：每个对象各查一遍选中的字段，结果按 UNION ALL 合起来再统一分页。
 * <p>与 {@link EntityUnionAggregateParam} 的分工：那个把值合批后只算一个聚合值，这个把行原样拼起来，
 * 一行还是那条对象实例的取值。字段按 apiName 共用，各对象上都按自己的属性表解析。</p>
 */
@Schema(description = "跨对象合并查询请求（UNION ALL）")
@Data
@SuperBuilder
@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
public class EntityUnionQueryParam {

    @Schema(name = "ontologyIdentifiers", description = "参与合并查询的对象id，至少一个；按顺序拼接各对象的结果", example = "[\"3f2a9c1e-7b4d-4a86-9e21-5c8d1f0a6b34\"]")
    private List<String> ontologyIdentifiers;

    @Schema(name = "entityPrimaryKeys", description = "各对象的实例主键：对象id -> 实例主键；某对象没有键时该对象按全量参与",
            example = "{\"3f2a9c1e-7b4d-4a86-9e21-5c8d1f0a6b34\":1}")
    private Map<String, Object> entityPrimaryKeys;

    @Schema(name = "selectProperties", description = "要返回的属性，各对象共用同一批 apiName；每个对象上都必须存在且已关联数据源",
            example = "[{\"propertyApiName\":\"name\"},{\"propertyApiName\":\"speed\"}]")
    private List<OntologySelectPropertyParam> selectProperties;

    @Schema(name = "filters", description = "过滤条件，各对象共用同一份（按各自的属性表解析）；不传则不加条件")
    private FilterGroupParam filters;

    @Schema(name = "pageNum", description = "分页号，作用在合并后的整批结果上，默认1")
    private Integer pageNum = 1;

    @Schema(name = "pageSize", description = "每页条数，作用在合并后的整批结果上，默认10")
    private Integer pageSize = 10;
}