package com.aircas.ptr.foundry.model.po;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;

import static com.aircas.ptr.foundry.common.constant.DateFormat.DATE_FORMAT_DEFAULT;


@Data
@Entity
@Table(name = "ontology_action")
public class OntologyAction {
    /**
     * Column: api
     */
    private String api;

    /**
     * Column: function_api
     */
    private String functionApi;

    /**
     * Column: ontology_unique_identifier
     */
    private String ontologyUniqueIdentifier;

    /**
     * Column: id
     */
    @Id
    @Column(name = "id")
    private Long id;

    private String description;

    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date updateTime;

    /**
     * 名称
     */
    private String displayName;

    /**
     * 临时，任务开始时间
     */
    private Date taskStartTime;

    /**
     * 临时，任务结束时间
     */
    private Date taskEndTime;

    /**
     * 临时，定时任务表达式
     */
    private String taskCorn;

    /**
     * 临时，任务的实体主键列表，以逗号分隔
     */
    private String objectPrimaryKey;

    private Integer status;

    private static final long serialVersionUID = 1L;
}