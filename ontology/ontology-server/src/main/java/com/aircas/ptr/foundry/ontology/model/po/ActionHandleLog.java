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

    private Date triggerTime;

    private Date completeTime;

    private TaskStatusEnum taskStatus;
}
