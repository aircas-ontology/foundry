package com.aircas.ptr.foundry.ontology.application.service.impl;

import com.aircas.ptr.foundry.model.po.ActionHandleRule;
import com.aircas.ptr.foundry.ontology.application.service.ActionHandleRuleService;
import com.aircas.ptr.foundry.ontology.entity.bo.ActionHandleRuleBO;
import com.aircas.ptr.foundry.ontology.repository.dao.ActionHandleRuleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @className: ActionHandleRuleServiceImpl
 * @author: yangj
 * @date: 2024/9/1 19:47
 * @version: 1.0
 * @description:
 */
@Service
@Slf4j
public class ActionHandleRuleServiceImpl implements ActionHandleRuleService {

    @Autowired
    private ActionHandleRuleMapper actionHandleRuleMapper;

    @Override
    public int insert(ActionHandleRuleBO actionHandleRuleBO) {

        ActionHandleRule actionHandleRule = new ActionHandleRule();
        BeanUtils.copyProperties(actionHandleRuleBO, actionHandleRule);

        return actionHandleRuleMapper.insert(actionHandleRule);
    }
}
