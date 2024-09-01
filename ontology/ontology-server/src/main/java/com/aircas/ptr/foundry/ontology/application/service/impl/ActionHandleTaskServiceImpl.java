package com.aircas.ptr.foundry.ontology.application.service.impl;

import com.aircas.ptr.foundry.model.po.ActionHandleTask;
import com.aircas.ptr.foundry.ontology.application.service.ActionHandleTaskService;
import com.aircas.ptr.foundry.ontology.entity.bo.ActionHandleTaskBO;
import com.aircas.ptr.foundry.ontology.repository.dao.ActionHandleTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tk.mybatis.mapper.entity.Example;

/**
 * @className: ActionHandleTaskServiceImpl
 * @author: yangj
 * @date: 2024/9/1 20:27
 * @version: 1.0
 * @description:
 */
@Service
@Slf4j
public class ActionHandleTaskServiceImpl implements ActionHandleTaskService {

    @Autowired
    private ActionHandleTaskMapper actionHandleTaskMapper;

    @Override
    public int insert(ActionHandleTaskBO actionHandleTaskBO) {

        ActionHandleTask actionHandleTask = new ActionHandleTask();
        BeanUtils.copyProperties(actionHandleTaskBO, actionHandleTask);

        return actionHandleTaskMapper.insert(actionHandleTask);
    }

    @Override
    public ActionHandleTaskBO selectByActionId(Long actionId) {

        ActionHandleTask actionHandleTask = new ActionHandleTask();
        actionHandleTask.setActionId(actionId);
        Example example = new Example(ActionHandleTask.class);
        example.createCriteria().andEqualTo("actionId", actionId);
        ActionHandleTask res = actionHandleTaskMapper.selectOneByExample(example);
        ActionHandleTaskBO actionHandleTaskBO = new ActionHandleTaskBO();
        BeanUtils.copyProperties(res, actionHandleTaskBO);
        return actionHandleTaskBO;
    }

    @Override
    public ActionHandleTaskBO selectById(Long taskId) {

        ActionHandleTask actionHandleTask = actionHandleTaskMapper.selectByPrimaryKey(taskId);
        ActionHandleTaskBO actionHandleTaskBO = new ActionHandleTaskBO();
        BeanUtils.copyProperties(actionHandleTask, actionHandleTaskBO);
        return actionHandleTaskBO;
    }
}
