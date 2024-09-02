package com.aircas.ptr.foundry.ontology.application.service;

import com.aircas.ptr.foundry.ontology.entity.bo.ActionHandleTaskBO;

/**
 * @interfaceName: ActionHandleTaskService
 * @author: yangj
 * @date: 2024/9/1 20:26
 * @version: 1.0
 * @description: 行为执行任务service
 */
public interface ActionHandleTaskService {

    int insert(ActionHandleTaskBO actionHandleTaskBO);

    ActionHandleTaskBO selectByActionId(Long actionId);

    ActionHandleTaskBO selectById(Long taskId);
}
