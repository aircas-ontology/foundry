package com.aircas.ptr.foundry.ontology.model.dto.elasticsearch;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

@Data
@Document(indexName = "ontology_property", createIndex = false)
public class OntologyPropertyDTO {

    /** 主键，对应 long id */
    @Id
    private Long id;

    /** api_name：keyword */
    @Field(name = "api_name", type = FieldType.Keyword)
    private String apiName;

    /** description：text */
    @Field(name = "description", type = FieldType.Text)
    private String description;

    /** display_name：text + keyword 子字段 */
    @Field(name = "display_name", type = FieldType.Text)
    private String displayName;

    /** is_primary_key：boolean */
    @Field(name = "is_primary_key", type = FieldType.Boolean)
    private Boolean isPrimaryKey;

    /** is_title_key：boolean */
    @Field(name = "is_title_key", type = FieldType.Boolean)
    private Boolean isTitleKey;

    /** ontology_unique_identifier：keyword */
    @Field(name = "ontology_unique_identifier", type = FieldType.Keyword)
    private String ontologyUniqueIdentifier;

    /** property_type：keyword */
    @Field(name = "property_type", type = FieldType.Keyword)
    private String propertyType;

    /** status：byte */
    @Field(name = "status", type = FieldType.Byte)
    private Integer status;

    /** unique_identifier：keyword */
    @Field(name = "unique_identifier", type = FieldType.Keyword)
    private String uniqueIdentifier;

    /** create_time：date，自定义多格式 */
    @Field(name = "create_time", type = FieldType.Date,
            format = {},
            pattern = "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd'T'HH:mm:ss.SSSZ||epoch_millis")
    private LocalDateTime createTime;

    /** update_time：date，自定义多格式 */
    @Field(name = "update_time", type = FieldType.Date,
            format = {},
            pattern = "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd'T'HH:mm:ss.SSSZ||epoch_millis")
    private LocalDateTime updateTime;
}