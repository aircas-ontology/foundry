package com.aircas.ptr.foundry.ontology.service;

import com.aircas.ptr.foundry.ontology.model.po.ActionHandleRule;
import com.aircas.ptr.foundry.ontology.model.bo.ActionHandleRuleBO;
import com.aircas.ptr.foundry.ontology.model.po.OntologyLinkGroup;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * @interfaceName: ActionHandleRule
 * @author: yangj
 * @date: 2024/9/1 19:45
 * @version: 1.0
 * @description: 行为执行规则service
 */
public interface ActionHandleRuleService  extends IService<ActionHandleRule> {

    int insert(ActionHandleRuleBO actionHandleRuleBO);


    List<ActionHandleRule> queryRulesByIds(List<Long> ids);

    int updateRules(ActionHandleRule rule);
}
