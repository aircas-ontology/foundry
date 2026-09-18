package com.aircas.ptr.foundry.ontology.model.dto.elasticsearch;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

@Data
@Document(indexName = "ontology_link_group", createIndex = false)
public class EsOntologyLinkGroupDTO {

    @Id
    private Long id;

    @Field(name = "create_time", type = FieldType.Date,
            format = {},
            pattern = "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd'T'HH:mm:ss.SSSZ||epoch_millis")
    private LocalDateTime createTime;

    @Field(name = "update_time", type = FieldType.Date,
            format = {},
            pattern = "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd'T'HH:mm:ss.SSSZ||epoch_millis")
    private LocalDateTime updateTime;

    @Field(name = "name", type = FieldType.Text)
    private String name;

    @Field(name = "ontology_space_id", type = FieldType.Long)
    private Long ontologySpaceId;

    @Field(name = "ontology_unique_identifier_from", type = FieldType.Keyword)
    private String ontologyUniqueIdentifierFrom;

    @Field(name = "ontology_unique_identifier_to", type = FieldType.Keyword)
    private String ontologyUniqueIdentifierTo;

    @Field(name = "status", type = FieldType.Byte)
    private Integer status;

    @Field(name = "type", type = FieldType.Keyword)
    private String type;

    @Field(name = "unique_identifier", type = FieldType.Keyword)
    private String uniqueIdentifier;
}