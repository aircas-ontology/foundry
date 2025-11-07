package com.aircas.ptr.foundry.ontology.model.document;

import com.aircas.ptr.foundry.common.constant.OntologyLinkTypeEnum;
import com.aircas.ptr.foundry.common.constant.Status;
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

    private String ontologyLinkId;

    //关系类型
    private OntologyLinkTypeEnum type;

    //关系名称
    private String name;

    private Date createTime;

    private Date updateTime;

    /**
     *  可见窗口开始时间
     */
    private Date startTime;

    /**
     *  可见窗口结束时间
     */
    private Date endTime;


    private Status status;


} 