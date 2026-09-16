package com.aircas.ptr.foundry.ontology.model.po;

import com.aircas.ptr.foundry.ontology.model.enums.ScheduleStatus;
import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Entity;
import java.util.Date;

import static com.aircas.ptr.foundry.common.constant.DateFormat.DATE_FORMAT_DEFAULT;

/**
 * @className: ActionHandleRule
 * @author: yangj
 * @date: 2024/9/1 18:00
 * @version: 1.0
 * @description: 本体行为执行规则
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@TableName(value = "action_handle_rule")
public class ActionHandleRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    private ScheduleStatus status;

    private Long actionId;

    private String name;

    private String description;

    private String rules;

    private Integer ruleConnectType;

    /**
     * 本体空间id
     */
    private Integer ontologySpaceId;

}
