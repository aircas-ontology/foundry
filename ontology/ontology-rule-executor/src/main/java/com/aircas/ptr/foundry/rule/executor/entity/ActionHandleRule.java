package com.aircas.ptr.foundry.rule.executor.entity;

import com.aircas.ptr.foundry.common.util.SnowflakeIdUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

import static com.aircas.ptr.foundry.common.constant.Status.ENABLE;

/**
 * @className: ActionHandleRuleBO
 * @author: yangj
 * @date: 2024/9/1 19:43
 * @version: 1.0
 * @description: 行为执行规则
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ActionHandleRule extends com.aircas.ptr.foundry.model.po.ActionHandleRule {

    public ActionHandleRule() {

        setId(SnowflakeIdUtil.get());
        Date now = new Date();
        setCreateTime(now);
        setUpdateTime(now);
        setStatus(ENABLE.getValue());
    }
}
