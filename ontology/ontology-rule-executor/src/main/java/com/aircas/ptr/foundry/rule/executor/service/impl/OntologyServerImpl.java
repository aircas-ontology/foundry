package com.aircas.ptr.foundry.rule.executor.service.impl;

import com.aircas.ptr.foundry.common.base.DataResult;
import com.aircas.ptr.foundry.model.po.ActionHandleCommitFlashMemory;
import com.aircas.ptr.foundry.rule.executor.config.RuleConfig;
import com.aircas.ptr.foundry.rule.executor.entity.ActionHandleRule;
import com.aircas.ptr.foundry.rule.executor.entity.OntologyAction;
import com.aircas.ptr.foundry.rule.executor.entity.OntologyMeta;
import com.aircas.ptr.foundry.rule.executor.entity.OntologyProperty;
import com.aircas.ptr.foundry.rule.executor.entity.param.ActionHandleParam;
import com.aircas.ptr.foundry.rule.executor.service.IOntologyServer;
import com.aircas.ptr.foundry.rule.executor.utils.RestUtil;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class OntologyServerImpl implements IOntologyServer {

    @Autowired
    private RuleConfig ruleConfig;
    @Override
    public List<OntologyMeta> getAllOntologies() {
        String url = ruleConfig.getOntologyServerRemote()+"/OntologyMeta/getAll";
        ParameterizedTypeReference<DataResult<List<OntologyMeta>>> responseType = new ParameterizedTypeReference<DataResult<List<OntologyMeta>>>() {};
        DataResult<List<OntologyMeta>>  dd = RestUtil.get(url,responseType);
        if (dd.succeed()){
            return dd.getDetail();
        }
        return null;
    }

    @Override
    public List<OntologyProperty> getAllOntologProprety() {
        String url = ruleConfig.getOntologyServerRemote()+"/OntologyProperty/getAllProperty?justPrimary=1";
        ParameterizedTypeReference<DataResult<List<OntologyProperty>>> responseType = new ParameterizedTypeReference<DataResult<List<OntologyProperty>>>() {};
        DataResult<List<OntologyProperty>>  dd = RestUtil.get(url,responseType);
        if (dd.succeed()){
            return dd.getDetail();
        }
        return null;
    }

    @Override
    public List<OntologyAction> queryByOntologyUniqueIdentifier(String ontologyUniqueIdentifier) {
        String url = ruleConfig.getOntologyServerRemote()+"/action/by_ontology?ontologyUniqueIdentifier="+ontologyUniqueIdentifier;
        ParameterizedTypeReference<DataResult<List<OntologyAction>>> responseType = new ParameterizedTypeReference<DataResult<List<OntologyAction>>>() {};
        DataResult<List<OntologyAction>>  dd = RestUtil.get(url,responseType);
        if (dd.succeed()){
            return dd.getDetail();
        }
        return null;
    }

    @Override
    public List<ActionHandleRule> queryActionRules(List<String> actionIds) {
        String url = ruleConfig.getOntologyServerRemote()+"/action/getActionRulesByIds?ids="+String.join(",",actionIds);
        ParameterizedTypeReference<DataResult<List<ActionHandleRule>>> responseType = new ParameterizedTypeReference<DataResult<List<ActionHandleRule>>>() {};
        DataResult<List<ActionHandleRule>>  dd = RestUtil.get(url,responseType);
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


    public ActionHandleCommitFlashMemory getActionDataLog(long actionId, String primaryValue){
        String url = ruleConfig.getOntologyServerRemote()+"/action/getActionDataLogByAction?actionId="+actionId+"&primaryValue="+primaryValue;
        ParameterizedTypeReference<DataResult<ActionHandleCommitFlashMemory>> responseType = new ParameterizedTypeReference<DataResult<ActionHandleCommitFlashMemory>>() {};
        DataResult<ActionHandleCommitFlashMemory>  dd = RestUtil.get(url,responseType);
        if (dd.succeed()){
            return dd.getDetail();
        }
        return null;
    }

    public int updateActionDataLog(ActionHandleCommitFlashMemory memory){
        String url = ruleConfig.getOntologyServerRemote()+"/action/updateActionDataLogByAction";
        DataResult  dd = RestUtil.post(url,memory,DataResult.class);
        return dd.succeed()?1:0;
    }

    @Override
    public boolean actionExecute(ActionHandleParam param) {
        try{
            String url = ruleConfig.getOntologyServerRemote()+"/action/execute";
            DataResult  dd = RestUtil.post(url,param,DataResult.class);
            log.info("执行行为 ["+param.getApi()+"] 结果:"+dd.succeed()+" 内容:"+ JSONObject.toJSONString(dd));
            return dd.succeed();
        }catch (Exception e){
            log.error("执行行为超时");
        }
        return false;

    }

}
