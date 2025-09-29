package com.aircas.ptr.foundry.ontology.model.view;

import com.aircas.ptr.foundry.ontology.model.po.OntologyActionMappingIn;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class OntologyActionView {

    // action info

    private Long actionId;

    private String api;

    /**
     * Column: function_api
     */
    private String functionApi;

    /**
     * Column: ontology_unique_identifier
     */
    private String ontologyUniqueIdentifier;


    private String description;

    /**
     * 名称
     */
    private String displayName;

    private Integer status;

    private Integer handleType;

    private Long ontologyLinkGroupId;

    //action rule

    /**
     * 实体主键列表，以逗号分隔
     */
    private String ruleObjectPrimaryKey;

    private String rules;

    private Integer ruleConnectType;

    private Integer ruleStatus;

    //action task

    /**
     * 实体主键列表，以逗号分隔
     */
    private String taskObjectPrimaryKey;

    /**
     * 任务开始时间
     */
    private Date startTime;

    /**
     * 任务结束时间
     */
    private Date endTime;

    /**
     * 定时任务表达式
     */
    private String corn;

    private Integer taskStatus;


    // action mapping
    private List<OntologyActionMappingIn> mappingIn;
}
