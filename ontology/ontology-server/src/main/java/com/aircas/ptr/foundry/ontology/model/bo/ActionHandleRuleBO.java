package com.aircas.ptr.foundry.ontology.model.bo;

import com.aircas.ptr.foundry.common.util.SnowflakeIdUtil;
import com.aircas.ptr.foundry.ontology.model.po.ActionHandleRule;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

import static com.aircas.ptr.foundry.ontology.model.enums.Status.ENABLE;

/**
 * @className: ActionHandleRuleBO
 * @author: yangj
 * @date: 2024/9/1 19:43
 * @version: 1.0
 * @description: 行为执行规则
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ActionHandleRuleBO extends ActionHandleRule {

    public ActionHandleRuleBO() {

        setId(SnowflakeIdUtil.get());
        Date now = new Date();
        setCreateTime(now);
        setUpdateTime(now);
        setStatus(ENABLE.getValue());
    }
}
