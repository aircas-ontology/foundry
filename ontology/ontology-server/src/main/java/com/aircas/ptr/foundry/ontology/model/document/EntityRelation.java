package com.aircas.ptr.foundry.ontology.model.document;

import com.aircas.ptr.foundry.ontology.model.enums.OntologyLinkTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.Status;
import com.aircas.ptr.foundry.ontology.model.common.VisibilityWindow;
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
import java.util.List;

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

    //本体关系id
    private String ontologyLinkId;

    //关系类型
    private OntologyLinkTypeEnum type;

    //关系名称
    private String name;

    private Date createTime;

    private Date updateTime;

    private Status status;


    /**
     *  可见窗口开始时间
     */
    private Date startTime;

    /**
     *  可见窗口结束时间
     */
    private Date endTime;

    /**
     * 可见窗口（包含过去3天-未来7天）
     */
    private List<VisibilityWindow> timeWindows;


} 