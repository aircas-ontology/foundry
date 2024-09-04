package com.aircas.ptr.foundry.rule.executor.service;

import com.aircas.ptr.foundry.common.base.DataResult;
import com.aircas.ptr.foundry.rule.executor.entity.ActionHandleRule;
import com.aircas.ptr.foundry.rule.executor.entity.OntologyAction;
import com.aircas.ptr.foundry.rule.executor.entity.OntologyMeta;
import com.aircas.ptr.foundry.rule.executor.entity.OntologyProperty;
import com.aircas.ptr.foundry.rule.executor.entity.param.ActionHandleParam;

import java.util.List;

public interface IOntologyServer {

//    查询所有本体的metadata
    List<OntologyMeta> getAllOntologies();

    List<OntologyProperty> getAllOntologProprety();

    // 根据本体Id 查询行为
    List<OntologyAction> queryByOntologyUniqueIdentifier(String ontologyUniqueIdentifier);

    List<ActionHandleRule> queryActionRules(List<String> actionIds);

    Boolean updateRuleInfo(ActionHandleRule rule);

    boolean actionExecute(ActionHandleParam param);

}
