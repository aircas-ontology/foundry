package com.aircas.ptr.foundry.ontology.model.document;

import com.arangodb.springframework.annotation.ArangoId;
import com.arangodb.springframework.annotation.Edge;
import com.arangodb.springframework.annotation.From;
import com.arangodb.springframework.annotation.To;
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
@Edge("relation")
public class EntityRelation {

    @Id
    private String id;

    @ArangoId
    private String arangoId;

    @From
    private EntityNode from;

    @To
    private EntityNode to;

    //关系类型
    private String type;

    //关系名称
    private String name;

    private String description;

    private Date createTime;                // 创建时间

    private Date updateTime;                // 更新时间

    private Boolean isDeleted;              // 是否删除



} 