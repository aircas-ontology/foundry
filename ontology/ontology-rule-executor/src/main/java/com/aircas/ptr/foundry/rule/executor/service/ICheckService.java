package com.aircas.ptr.foundry.rule.executor.service;

import com.aircas.ptr.foundry.model.po.ActionHandleCommitFlashMemory;
import com.aircas.ptr.foundry.rule.executor.entity.ActionHandleRule;
import com.aircas.ptr.foundry.rule.executor.entity.OntologyMeta;
import com.aircas.ptr.foundry.rule.executor.entity.OntologyProperty;
import com.aircas.ptr.foundry.rule.executor.entity.dynamics.CheckRuleStatus;
import com.aircas.ptr.foundry.rule.executor.entity.vo.CheckDataVO;

import java.util.List;

public interface ICheckService {

    List<OntologyMeta> getMeta(String table);

    List<OntologyProperty> getPropertyList(String ontologyUniqueIdentifier);

    CheckRuleStatus checkRule(CheckDataVO vo, ActionHandleRule rule, List<OntologyProperty> propertyList);


    String getNewRules(CheckDataVO vo,ActionHandleRule rule);

    ActionHandleCommitFlashMemory getNewflashmemory(CheckDataVO vo,ActionHandleRule rule,CheckRuleStatus status);

    Object getPrimaryValue(CheckDataVO vo,List<OntologyProperty> propertyList);
}
