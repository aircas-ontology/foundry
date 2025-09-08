package com.aircas.ptr.foundry.ontology.model.bo;

import com.aircas.ptr.foundry.common.util.SnowflakeIdUtil;
import com.aircas.ptr.foundry.model.po.ActionHandleTask;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

import static com.aircas.ptr.foundry.common.constant.Status.ENABLE;

/**
 * @className: ActionHandleTaskBO
 * @author: yangj
 * @date: 2024/9/1 20:27
 * @version: 1.0
 * @description: 行为执行任务
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ActionHandleTaskBO  extends ActionHandleTask {

    public ActionHandleTaskBO() {

        setId(SnowflakeIdUtil.get());
        Date now = new Date();
        setCreateTime(now);
        setUpdateTime(now);
        setStatus(ENABLE.getValue());
    }
}
