package com.aircas.ptr.foundry.ontology.model.dto.elasticsearch;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Routing;
import org.springframework.data.elasticsearch.annotations.WriteTypeHint;

import java.util.List;

/**
 * 数据湖实例对象 ES 文档，对应索引 ontology_instance。
 * <p>
 * 一条文档 = entity_datasource 库中某张已绑定本体主键属性的表的一行记录。
 * 索引 mapping 为 dynamic=strict，文档只能包含下方已映射字段；
 * _id 与 routing 均使用 {@code pk}（格式 {schema}.{table_name}.{主键列值}，保证跨表唯一）。
 * <p>
 * writeTypeHint = FALSE：禁止 spring-data 写入 "_class" 类型提示字段，
 * 避免被 dynamic=strict 的索引拒绝（索引建索引脚本见 deploy/elasticsearch/，其中也映射了 _class 以兼容历史数据）。
 */
@Data
@Document(indexName = "ontology_instance", createIndex = false, writeTypeHint = WriteTypeHint.FALSE)
@Routing("pk")
public class EsOntologyInstanceDTO {

    /** 文档唯一键：{schema}.{table_name}.{主键列值}，同时作为 routing（索引要求 _routing.required） */
    @Id
    @Field(name = "pk", type = FieldType.Keyword)
    private String pk;

    /** 文档类型，区分索引内不同类别的文档，实例对象固定为 ontology_instance */
    @Field(name = "doc_type", type = FieldType.Keyword)
    private String docType;

    /** 数据湖表名（datasource_id） */
    @Field(name = "table_name", type = FieldType.Keyword)
    private String tableName;

    /** 数据湖 schema（本体空间 api_name） */
    @Field(name = "schema", type = FieldType.Text)
    private String schema;

    /** 实例显示名称（名称键列值，缺省取主键值） */
    @Field(name = "name", type = FieldType.Text, analyzer = "od_index", searchAnalyzer = "od_search")
    private String name;

    /** 名称联想（completion）。索引 mapping 由外部 PUT 建立（createIndex=false），
     *  此处以 Object 承载，序列化后为字符串数组，即 completion 的 input。 */
    @Field(name = "name_suggest", type = FieldType.Object)
    private List<String> nameSuggest;

    /** 全文检索文本：行内所有列值拼接 */
    @Field(name = "search_text", type = FieldType.Text, analyzer = "od_index", searchAnalyzer = "od_search")
    private String searchText;

    /** 所属本体 unique_identifier */
    @Field(name = "ontology_uid", type = FieldType.Keyword)
    private String ontologyUid;

    /** 所属本体显示名 */
    @Field(name = "ontology_name", type = FieldType.Keyword)
    private String ontologyName;

    /** 所属本体 api_name */
    @Field(name = "api_name", type = FieldType.Keyword)
    private String apiName;

    /** 所属本体空间 id */
    @Field(name = "space_id", type = FieldType.Integer)
    private Integer spaceId;

    /** 所属本体空间显示名 */
    @Field(name = "space_display_name", type = FieldType.Keyword)
    private String spaceDisplayName;

    /** 所属本体空间 api_name */
    @Field(name = "space_api_name", type = FieldType.Keyword)
    private String spaceApiName;
}
