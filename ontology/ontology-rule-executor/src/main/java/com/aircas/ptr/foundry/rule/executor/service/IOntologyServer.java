package com.aircas.ptr.foundry.rule.executor.service;

import com.aircas.ptr.foundry.common.base.DataResult;
import com.aircas.ptr.foundry.model.po.ActionHandleCommitFlashMemory;
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

    /**
     *  查询行为的规则信息
     * @param actionIds
     * @return
     */
    List<ActionHandleRule> queryActionRules(List<String> actionIds);

    ActionHandleCommitFlashMemory getActionDataLog(long actionId,String primaryValue);

    int updateActionDataLog(ActionHandleCommitFlashMemory memory);

    Boolean updateRuleInfo(ActionHandleRule rule);

    boolean actionExecute(ActionHandleParam param);

}
