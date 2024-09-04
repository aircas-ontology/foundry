package com.aircas.ptr.foundry.rule.executor.service.impl;

import com.aircas.ptr.foundry.common.base.DataResult;
import com.aircas.ptr.foundry.rule.executor.config.RuleConfig;
import com.aircas.ptr.foundry.rule.executor.entity.ActionHandleRule;
import com.aircas.ptr.foundry.rule.executor.entity.OntologyAction;
import com.aircas.ptr.foundry.rule.executor.entity.OntologyMeta;
import com.aircas.ptr.foundry.rule.executor.entity.OntologyProperty;
import com.aircas.ptr.foundry.rule.executor.entity.param.ActionHandleParam;
import com.aircas.ptr.foundry.rule.executor.service.IOntologyServer;
import com.aircas.ptr.foundry.rule.executor.utils.RestUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OntologyServerImpl implements IOntologyServer {

    @Autowired
    private RuleConfig ruleConfig;
    @Override
    public List<OntologyMeta> getAllOntologies() {
        String url = ruleConfig.getOntologyServerRemote()+"/OntologyMeta/getAll";
        DataResult<List<OntologyMeta>>  dd = RestUtil.get(url,DataResult.class);
        if (dd.succeed()){
            return dd.getDetail();
        }
        return null;
    }

    @Override
    public List<OntologyProperty> getAllOntologProprety() {
        String url = ruleConfig.getOntologyServerRemote()+"/OntologyProperty/getAllProperty?justPrimary=1";
        DataResult<List<OntologyProperty>>  dd = RestUtil.get(url,DataResult.class);
        if (dd.succeed()){
            return dd.getDetail();
        }
        return null;
    }

    @Override
    public List<OntologyAction> queryByOntologyUniqueIdentifier(String ontologyUniqueIdentifier) {
        String url = ruleConfig.getOntologyServerRemote()+"/action/by_ontology?ontologyUniqueIdentifier="+ontologyUniqueIdentifier;
        DataResult<List<OntologyAction>>  dd = RestUtil.get(url,DataResult.class);
        if (dd.succeed()){
            return dd.getDetail();
        }
        return null;
    }

    @Override
    public List<ActionHandleRule> queryActionRules(List<String> actionIds) {
        String url = ruleConfig.getOntologyServerRemote()+"/action/getActionRulesByIds?ids="+String.join(",",actionIds);
        DataResult<List<ActionHandleRule>>  dd = RestUtil.get(url,DataResult.class);
        if (dd.succeed()){
            return dd.getDetail();
        }
        return null;
    }

    @Override
    public Boolean updateRuleInfo(ActionHandleRule rule) {
        String url = ruleConfig.getOntologyServerRemote()+"/action/updateActionRules";
        DataResult  dd = RestUtil.post(url,rule,DataResult.class);
        return dd.succeed();
    }

    @Override
    public boolean actionExecute(ActionHandleParam param) {
        String url = ruleConfig.getOntologyServerRemote()+"/action/execute";
        DataResult  dd = RestUtil.post(url,param,DataResult.class);
        return false;
    }

}
