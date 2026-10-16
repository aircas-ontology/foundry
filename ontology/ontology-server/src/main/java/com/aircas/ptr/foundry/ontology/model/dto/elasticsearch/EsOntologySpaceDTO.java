package com.aircas.ptr.foundry.ontology.model.dto.elasticsearch;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

@Data
@Document(indexName = "ontology_space", createIndex = false)
public class EsOntologySpaceDTO {

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

    /** username：keyword，创建该空间/主题的用户名 */
    @Field(name = "username", type = FieldType.Keyword)
    private String username;

    /** type：short，0=空间，1=主题 */
    @Field(name = "type", type = FieldType.Short)
    private Integer type;

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