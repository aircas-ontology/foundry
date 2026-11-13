package com.aircas.ptr.foundry.ontology.model.po;

import com.aircas.ptr.foundry.ontology.model.enums.ScheduleStatus;
import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import jakarta.persistence.Entity;
import java.util.Date;

import static com.aircas.ptr.foundry.common.constant.DateFormat.DATE_FORMAT_DEFAULT;

/**
 * @className: ActionHandleRule
 * @author: yangj
 * @date: 2024/9/1 18:00
 * @version: 1.0
 * @description: 对象行为执行规则
 */
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
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

    private String actionApi;

    private String ontologyIdentifier;

    private String name;

    private String description;

    /**
     * 定稿规则结构 JSON（List&lt;RuleJudgeGroupParam&gt;）：组合条件（括号+AND/OR，可整体取反）+ 行为执行参数。
     * 条件跨对象，CDC 按条件引用的实例定向触发；规则启用时先评估一遍存量数据
     */
    private String judgeJson;

    /**
     * 对象空间id
     */
    private Integer ontologySpaceId;

}
