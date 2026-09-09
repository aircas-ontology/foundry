package com.aircas.ptr.foundry.ontology.model.po;

import com.aircas.ptr.foundry.ontology.model.enums.ScheduleStatus;
import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import javax.persistence.Entity;
import java.util.Date;

import static com.aircas.ptr.foundry.common.constant.DateFormat.DATE_FORMAT_DEFAULT;

/**
 * @className: ActionHandleTask
 * @author: yangj
 * @date: 2024/9/1 20:22
 * @version: 1.0
 * @description: 行为执行任务
 */
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Accessors(chain = true)
@Entity
@TableName(value = "action_handle_task")
public class ActionHandleTask {

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


    /**
     * 定时任务表达式
     */
    private String cron;

    private String name;

    private String description;

    private String remark;

    /**
     * 本体空间id
     */
    private Integer ontologySpaceId;

}
