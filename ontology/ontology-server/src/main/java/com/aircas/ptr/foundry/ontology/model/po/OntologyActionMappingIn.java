package com.aircas.ptr.foundry.ontology.model.po;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.Date;

import static com.aircas.ptr.foundry.common.constant.DateFormat.DATE_FORMAT_DEFAULT;

@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@Data
@Entity
@TableName("ontology_action_mapping_in")
public class OntologyActionMappingIn {
    /**
     * Column: id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Column: ontology_function_id
     * Remark: function的名称
     */
    private Long ontologyActionId;

    /**
     * 函数参数id
     */
    private Long functionParamId;

    /**
     * 关联的函数参数表达式
     */
    private String functionParamExpression;

    /**
     * Column: property_unique_identifier
     * Remark: 属性的唯一标识，如果为this, 则表示当前本体对象。
     */
    private String propertyUniqueIdentifier;

    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    private static final long serialVersionUID = 1L;
}