package com.aircas.ptr.foundry.ontology.model.document;

import com.arangodb.springframework.annotation.ArangoId;
import com.arangodb.springframework.annotation.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Document("node")
public class EntityNode {

    @Id
    private String id;

    @ArangoId
    private String arangoId;

    private String ontologyUniqIdentifier;

    private String tableName;

    private Object primaryKey;

    private String displayName;

    private Date createTime;

    private Date updateTime;

    private Boolean isDeleted;


} 