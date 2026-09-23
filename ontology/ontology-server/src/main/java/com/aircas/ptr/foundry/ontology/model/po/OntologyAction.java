package com.aircas.ptr.foundry.ontology.model.po;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Date;

import static com.aircas.ptr.foundry.common.constant.DateFormat.DATE_FORMAT_DEFAULT;

@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
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
    @TableId(type = IdType.AUTO)
    private Long id;

    private String description;

    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /**
     * 名称
     */
    private String displayName;

    private Integer status;

    private String icon;

    /**
     * 所属行为分类id，对应 action_category.id
     */
    private Integer actionCategoryId;

    /**
     * 本体空间id
     */
    private Integer ontologySpaceId;


    private static final long serialVersionUID = 1L;
}