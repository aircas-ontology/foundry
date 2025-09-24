package com.aircas.ptr.foundry.model.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.Getter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;

import static com.aircas.ptr.foundry.common.constant.DateFormat.DATE_FORMAT_DEFAULT;

/**
 * @className: ActionHandleTask
 * @author: yangj
 * @date: 2024/9/1 20:22
 * @version: 1.0
 * @description: 行为执行任务
 */
@Data
@Entity
@TableName(value = "action_handle_task")
public class ActionHandleTask {

    @Id
    @Column(name = "id")
    private Long id;

    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = DATE_FORMAT_DEFAULT, timezone = "GMT+8")
    private Date updateTime;

    private Integer status;

    private Long actionId;

    /**
     * 实体主键列表，以逗号分隔
     */
    private String objectPrimaryKey;

    /**
     * 任务开始时间
     */
    private Date startTime;

    /**
     * 任务结束时间
     */
    private Date endTime;

    /**
     * 定时任务表达式
     */
    private String corn;

}
