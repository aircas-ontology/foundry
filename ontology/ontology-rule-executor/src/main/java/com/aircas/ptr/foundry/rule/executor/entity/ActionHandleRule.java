package com.aircas.ptr.foundry.rule.executor.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.persistence.Column;
import javax.persistence.Id;
import java.util.Date;

import static com.aircas.ptr.foundry.common.constant.DateFormat.DATE_FORMAT_DEFAULT;

@Data
public class ActionHandleRule{

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

    private String rules;

    private Integer ruleConnectType;
}
