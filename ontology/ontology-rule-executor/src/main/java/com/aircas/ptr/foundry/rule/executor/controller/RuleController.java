package com.aircas.ptr.foundry.rule.executor.controller;

import com.aircas.ptr.foundry.common.base.ApiResult;
import com.aircas.ptr.foundry.common.base.DataResult;
import com.aircas.ptr.foundry.rule.executor.entity.ActionHandleRule;
import com.aircas.ptr.foundry.rule.executor.entity.OntologyAction;
import com.aircas.ptr.foundry.rule.executor.entity.OntologyMeta;
import com.aircas.ptr.foundry.rule.executor.entity.OntologyProperty;
import com.aircas.ptr.foundry.rule.executor.entity.param.ActionHandleParam;
import com.aircas.ptr.foundry.rule.executor.entity.vo.CheckDataVO;
import com.aircas.ptr.foundry.rule.executor.service.ICheckService;
import com.aircas.ptr.foundry.rule.executor.service.IOntologyServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/rule")
public class RuleController {

    @Autowired
    private ICheckService checkService;

    @Autowired
    private IOntologyServer ontologyServer;

    @PostMapping("/check/action")
    public ApiResult checkData(@RequestBody CheckDataVO checkDataVO){
        // get ontology
        List<OntologyMeta> metaList = checkService.getMeta(checkDataVO.getTable());
        if (metaList ==null){
            return  DataResult.fail("没有对应的实体信息");
        }
        for (OntologyMeta meta : metaList){
            log.info("======= 开始检测实体数据变更有效性检查 apiName:"+meta.getApiName()+" =======");
            List<OntologyProperty> propertyList = checkService.getPropertyList(meta.getUniqueIdentifier());

            List<OntologyAction> ontologyActions = ontologyServer.queryByOntologyUniqueIdentifier(meta.getUniqueIdentifier());
            // 查询行为规则
            List<String> ids = new ArrayList<>(ontologyActions.size());
            for (OntologyAction action : ontologyActions){
                ids.add(action.getId()+"");
            }
            List<ActionHandleRule> ruls= ontologyServer.queryActionRules(ids);
            for (ActionHandleRule rule : ruls){
                if (checkService.checkRule(checkDataVO,rule,propertyList)){
                    // 规则检验通过
                    // 更新规则历史信息
                    String rules = checkService.getNewRules(checkDataVO,rule);
                    rule.setRules(rules);
                    if (ontologyServer.updateRuleInfo(rule)){
                        //TODO 如果执行失败 是否要停止执行函数？
                    }
                    Object value = checkService.getPrimaryValue(checkDataVO,propertyList);
                    if (value ==null){
                        System.out.println("");
                        return null;
                    }
                    // 执行规则绑定的函数信息
                    ActionHandleParam param = new ActionHandleParam();
                    param.setApi(meta.getApiName());
                    param.setPrimaryKey(value.toString());
                    ontologyServer.actionExecute(param);
                }
            }
        }

        return null;
    }
}
