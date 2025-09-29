package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.bo.ActionHandleTaskBO;
import com.aircas.ptr.foundry.ontology.model.po.ActionHandleTask;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * @interfaceName: ActionHandleTaskService
 * @author: yangj
 * @date: 2024/9/1 20:26
 * @version: 1.0
 * @description: 行为执行任务service
 */
public interface ActionHandleTaskService extends IService<ActionHandleTask> {

    int insert(ActionHandleTaskBO actionHandleTaskBO);

    ActionHandleTaskBO selectByActionId(Long actionId);

    ActionHandleTaskBO selectById(Long taskId);
}
