package com.aircas.ptr.foundry.ontology.model.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
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
@TableName("ontology_action_link")
public class OntologyActionLink {
    /**
     * Column: id
     */
    @Id
    @Column(name = "id")
    private Long id;


    private Long ontologyActionId;

    /**
     * 关联的本体关系
     */
    private String ontologyLinkUniqueIdentifier;

    /**
     * 行为绑定的关系存在时的参数表达式,例如data.startTime>current time && data.distance>1000
     */
    private String ontologyLinkParamExpression;

    /**
     * 开始时间关联的函数参数id
     */
    private Long startTimeFunctionParamId;

    /**
     * 开始时间关联的函数参数的表达式
     */
    private String startTimeFunctionParamExpression;

    /**
     * 结束时间关联的函数参数id
     */
    private Long endTimeFunctionParamId;

    /**
     * 结束时间关联的函数参数的表达式
     */
    private String endTimeFunctionParamExpression;


    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    private static final long serialVersionUID = 1L;
}