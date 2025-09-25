package com.aircas.ptr.foundry.ontology.service.impl;

import com.aircas.ptr.foundry.ontology.model.po.ActionHandleRule;
import com.aircas.ptr.foundry.ontology.service.ActionHandleRuleService;
import com.aircas.ptr.foundry.ontology.model.bo.ActionHandleRuleBO;
import com.aircas.ptr.foundry.ontology.repository.dao.ActionHandleRuleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

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

    @Override
    public List<ActionHandleRule> queryRulesByIds(List<Long> ids) {
        if (ids==null || ids.size()==0){
            return null;
        }
        return actionHandleRuleMapper.queryRulesById(ids);
    }

    @Override
    public int updateRules(ActionHandleRule rule) {
        return actionHandleRuleMapper.updateRulesById(rule);
    }
}
