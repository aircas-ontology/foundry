package com.aircas.ptr.foundry.ontology.entity.bo;

import com.aircas.ptr.foundry.common.util.SnowflakeIdUtil;
import com.aircas.ptr.foundry.model.po.ActionHandleCommitFlashMemory;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;


/**
 * @className: ActionHandleRuleBO
 * @author: yangj
 * @date: 2024/9/1 19:43
 * @version: 1.0
 * @description: 行为执行规则
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ActionHandleCommitFlashMemoryBO extends ActionHandleCommitFlashMemory {

    public ActionHandleCommitFlashMemoryBO() {
        setId(SnowflakeIdUtil.get());
        Date now = new Date();
        setCreateTime(now);
        setUpdateTime(now);
    }
}
