package com.aircas.ptr.foundry.rule.executor.service.impl;

import com.aircas.ptr.foundry.common.base.ApiResult;
import com.aircas.ptr.foundry.common.base.DataResult;
import com.aircas.ptr.foundry.rule.executor.entity.ActionHandleRule;
import com.aircas.ptr.foundry.rule.executor.entity.OntologyAction;
import com.aircas.ptr.foundry.rule.executor.entity.OntologyMeta;
import com.aircas.ptr.foundry.rule.executor.entity.OntologyProperty;
import com.aircas.ptr.foundry.rule.executor.entity.dynamics.CheckRuleStatus;
import com.aircas.ptr.foundry.rule.executor.entity.param.ActionHandleParam;
import com.aircas.ptr.foundry.rule.executor.entity.vo.CheckDataVO;
import com.aircas.ptr.foundry.rule.executor.service.ICheckService;
import com.aircas.ptr.foundry.rule.executor.service.IOntologyServer;
import com.aircas.ptr.foundry.rule.executor.service.IRuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class RuleServiceImpl implements IRuleService {

    @Autowired
    private ICheckService checkService;

    @Autowired
    private IOntologyServer ontologyServer;

    @Override
    public ApiResult checkData(CheckDataVO checkDataVO) {
        // get ontology
        List<OntologyMeta> metaList = checkService.getMeta(checkDataVO.getTable());
        if (metaList ==null){
            return  DataResult.fail("没有对应的实体信息");
        }
        for (OntologyMeta meta : metaList){
            log.info("======= 开始检测实体数据变更有效性检查 apiName:"+meta.getApiName()+" =======");
            List<OntologyProperty> propertyList = checkService.getPropertyList(meta.getUniqueIdentifier());

            List<OntologyAction> ontologyActions = ontologyServer.queryByOntologyUniqueIdentifier(meta.getUniqueIdentifier());
            if (ontologyActions==null || ontologyActions.size()==0){
                log.info(meta.getApiName()+" 实体没有绑定行为信息...:"+meta.getUniqueIdentifier());
                continue;
            }
            // 查询行为规则
            List<String> ids = new ArrayList<>(ontologyActions.size());
            for (OntologyAction action : ontologyActions){
                ids.add(action.getId()+"");
            }
            List<ActionHandleRule> ruls= ontologyServer.queryActionRules(ids);
            log.info(String.join(",",ids)+" 绑定的行为规则数量:"+ruls.size());
            for (ActionHandleRule rule : ruls){
                CheckRuleStatus checkRuleStatus = checkService.checkRule(checkDataVO,rule,propertyList);
                if (checkRuleStatus.isStatus()){
                    // 规则检验通过
                    // 更新规则历史信息
//                    String rules = checkService.getNewRules(checkDataVO,rule);
//                    rule.setRules(rules);
                    if (ontologyServer.updateActionDataLog(checkService.getNewflashmemory(checkDataVO,rule,checkRuleStatus))==0){
                        //TODO 如果执行失败 是否要停止执行函数？
                    }
                    Object value = checkService.getPrimaryValue(checkDataVO,propertyList);
                    if (value ==null){
                        log.warn("数据源中没有主键信息,更新表:"+checkDataVO.getTable());
                        return null;
                    }
                    // 执行规则绑定的函数信息
                    for (OntologyAction action : ontologyActions){
                        if (action.getId().longValue() == rule.getActionId().longValue()){
                            ActionHandleParam param = new ActionHandleParam();
                            param.setApi(action.getApi());
                            param.setPrimaryKey(value.toString());
                            ontologyServer.actionExecute(param);
                        }
                    }

                }else{
                    log.info("规则校验未通过，规则内容："+rule.getRules());
                }
            }
        }
        return ApiResult.success();
    }
}
