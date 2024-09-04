package com.aircas.ptr.foundry.ontology.application.service;

import com.aircas.ptr.foundry.model.po.ActionHandleRule;
import com.aircas.ptr.foundry.ontology.entity.bo.ActionHandleRuleBO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @interfaceName: ActionHandleRule
 * @author: yangj
 * @date: 2024/9/1 19:45
 * @version: 1.0
 * @description: 行为执行规则service
 */
public interface ActionHandleRuleService {

    int insert(ActionHandleRuleBO actionHandleRuleBO);


    List<ActionHandleRule> queryRulesByIds(List<String> ids);

    int updateRules(ActionHandleRule rule);
}
