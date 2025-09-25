package com.aircas.ptr.foundry.ontology.model.po;

import com.baomidou.mybatisplus.annotation.TableName;
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
@TableName(value = "ontology_action")
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

    private Integer status;

    private Integer handleType;

    private static final long serialVersionUID = 1L;
}