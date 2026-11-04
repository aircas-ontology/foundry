package com.aircas.ptr.foundry.ontology.model.po;

import com.aircas.ptr.foundry.ontology.model.enums.ActionKindEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ActionOperationEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ActionOutputModeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ActionSubjectTypeEnum;
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
     * 编辑行为时按类别整体重写，改为基础行为（不绑算子）后要置空，故空值也参与 UPDATE
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
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

    /**
     * 行为状态：2 草稿 / 1 已发布
     *
     * @see com.aircas.ptr.foundry.ontology.model.enums.ActionStatusEnum
     */
    private Integer status;

    private String icon;

    /**
     * 所属行为分类id，对应 action_category.id
     */
    private Integer actionCategoryId;

    /**
     * 对象空间id
     */
    private Integer ontologySpaceId;

    /**
     * 行为类别：BASE 基础行为 / APPLICATION 应用行为
     */
    private ActionKindEnum actionKind;

    /**
     * 基础行为的类型，应用行为为空
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private ActionSubjectTypeEnum subjectType;

    /**
     * 基础操作，仅属性值、实例关系两类有值
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private ActionOperationEnum actionOperation;

    /**
     * 属性值操作的目标存储分组，分组名即物理表名
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String storageGroup;

    /**
     * 输出方式；属性值、实例关系两类没有输出回写，为空
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private ActionOutputModeEnum outputMode;


    private static final long serialVersionUID = 1L;
}