package com.aircas.ptr.foundry.model.po;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;


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

    /**
     * isPreview == 1, 则是测试模式下，使用完后要删除
     */
    private boolean isPreview;

    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private Date createTime;

    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private Date updateTime;

    /**
     * 名称
     */
    private String displayName;

    /**
     * 任务开始时间
     */
    private Date taskStartTime;

    /**
     * 任务结束时间
     */
    private Date taskEndTime;

    /**
     * 定时任务表达式
     */
    private String taskCorn;

    private static final long serialVersionUID = 1L;
}