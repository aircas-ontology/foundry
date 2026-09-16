package com.aircas.ptr.foundry.ontology.model.po;

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
@TableName("ontology_action_link")
public class OntologyActionLink {
    /**
     * Column: id
     */
    @TableId(type = IdType.AUTO)
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


    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    private static final long serialVersionUID = 1L;
}