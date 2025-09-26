package com.aircas.ptr.foundry.ontology.entity.model.document;

import com.arangodb.springframework.annotation.ArangoId;
import com.arangodb.springframework.annotation.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.util.Date;
import java.util.Map;

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

    private String tableName;

    private Object primaryKey;

    private Date createTime;                // 创建时间

    private Date updateTime;                // 更新时间

    private Boolean isDeleted;              // 是否删除


} 