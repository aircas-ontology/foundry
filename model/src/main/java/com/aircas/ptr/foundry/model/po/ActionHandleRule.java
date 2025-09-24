package com.aircas.ptr.foundry.model.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;

import static com.aircas.ptr.foundry.common.constant.DateFormat.DATE_FORMAT_DEFAULT;

/**
 * @className: ActionHandleRule
 * @author: yangj
 * @date: 2024/9/1 18:00
 * @version: 1.0
 * @description: 本体行为执行规则
 */
@Data
@Entity
@TableName(value = "action_handle_rule")
public class ActionHandleRule {

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
    @Column(name = "object_primary_key")
    private String objectPrimaryKey;

    private String rules;

    @Column(name = "rule_connect_type")
    private Integer ruleConnectType;
}
