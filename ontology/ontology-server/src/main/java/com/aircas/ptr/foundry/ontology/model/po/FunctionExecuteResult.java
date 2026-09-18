package com.aircas.ptr.foundry.ontology.model.po;

import com.aircas.ptr.foundry.ontology.model.enums.TaskStatusEnum;
import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import jakarta.persistence.Entity;
import java.util.Date;


@Data
@Entity
@Builder
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "function_execute_result")
public class FunctionExecuteResult {


    /**
     * 记录创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;


    /**
     * 记录更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /**
     * Column: task_id
     */
    private String taskId;

    /**
     * Column: result
     */
    private String result;

    /**
     * Column: function_api
     */
    private String functionApi;

    /**
     * Column: action_api
     */
    private String actionApi;

    /**
     * Column: id
     */
    @TableId(type = IdType.AUTO)
    private Integer id;


    /**
     * action_context_info
     */
    private String actionContextInfo;

    private String functionParam;

    private TaskStatusEnum taskStatus;


}