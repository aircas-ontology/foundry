package com.aircas.ptr.foundry.ontology.model.po;

import com.aircas.ptr.foundry.ontology.model.enums.ActionParamSourceEnum;
import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
     * Column: parameter_name
     * Remark: 参数的名字
     */
    private String parameterName;

    /**
     * Column: property_unique_identifier
     * Remark: 属性的唯一标识，如果为this, 则表示当前对象。
     */
    private String propertyUniqueIdentifier;

    /**
     * 输入来源：对象属性 / 对象 / List / 固定值 / 外部输入
     */
    private ActionParamSourceEnum sourceType;

    /**
     * 对象入参的子字段路径（相对该算子参数，点分层级，如 position.longitude），标量参数或整包取值为空
     */
    private String paramPath;

    /**
     * 输入来源配置（JSON）：固定值存字面量，List 存多个属性唯一标识
     */
    private String sourceConfig;

    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    private static final long serialVersionUID = 1L;
}