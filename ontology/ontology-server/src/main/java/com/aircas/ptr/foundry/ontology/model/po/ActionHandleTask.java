package com.aircas.ptr.foundry.ontology.model.po;

import com.aircas.ptr.foundry.ontology.model.enums.ScheduleStatus;
import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;

import jakarta.persistence.Entity;
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

    private String actionApi;

    private String ontologyIdentifier;

    /**
     * 定时任务表达式
     */
    private String cron;

    private String name;

    private String description;

    /**
     * 实体执行参数 JSON：定时调度触发时反序列化成 EntityActionExecuteParam，按它指定的实例执行；
     * 为空则回退按对象下全部实例遍历执行。
     */
    private String entityJson;

    private String remark;

    /**
     * 对象空间id
     */
    private Integer ontologySpaceId;

}
