package com.aircas.ptr.foundry.ontology.model.po;

import com.aircas.ptr.foundry.ontology.model.enums.ActionSchedulingTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.TaskStatusEnum;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

import jakarta.persistence.Entity;
import java.util.Date;

@Data
@Builder
@Accessors(chain = true)
@Entity
@TableName(value = "action_handle_log")
public class ActionHandleLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long actionHandleId;

    private ActionSchedulingTypeEnum actionHandleType;

    private String requestParam;

    private String msg;

    private String executeResult;

    private Date triggerTime;

    /** 实际开始执行行为的时间（触发后可能排队/评估，与 triggerTime 区分） */
    private Date executeStartTime;

    private Date completeTime;

    /** 执行机 IP */
    private String executorIp;

    /** 执行机主机名 */
    private String executorHost;

    private TaskStatusEnum taskStatus;
}
