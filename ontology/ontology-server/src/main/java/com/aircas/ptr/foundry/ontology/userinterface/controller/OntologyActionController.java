package com.aircas.ptr.foundry.ontology.userinterface.controller;

import com.aircas.ptr.foundry.common.base.ApiResult;
import com.aircas.ptr.foundry.common.base.DataResult;

import com.aircas.ptr.foundry.model.po.ActionHandleRule;
import com.aircas.ptr.foundry.ontology.Exception.*;
import com.aircas.ptr.foundry.ontology.application.service.ActionHandleTaskService;
import com.aircas.ptr.foundry.ontology.application.service.DynamicActionTaskService;
import com.aircas.ptr.foundry.ontology.application.service.OntologyActionService;
import com.aircas.ptr.foundry.ontology.entity.bo.ActionHandleTaskBO;
import com.aircas.ptr.foundry.ontology.repository.param.ActionHandleConfigParam;
import com.aircas.ptr.foundry.ontology.repository.param.ActionHandleParam;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyActionBo;
import com.aircas.ptr.foundry.ontology.entity.bo.OntologyActionMappingInBO;
import com.aircas.ptr.foundry.ontology.entity.vo.OntologyActionVO;
import com.aircas.ptr.foundry.ontology.repository.param.ActionAddParam;
import com.github.pagehelper.PageInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.quartz.SchedulerException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


@Api(tags = "行为")
@RestController
@RequestMapping("/action")
public class OntologyActionController {

    @Resource
    private OntologyActionService ontologyActionService;

    @Autowired
    private DynamicActionTaskService dynamicActionTaskService;

    @Autowired
    private ActionHandleTaskService actionHandleTaskService;

    @ApiOperation(value = "新增行为")
    @PostMapping("/meta")
    public ApiResult metaSave(@RequestBody ActionAddParam param) throws OntologyFunctionParameterPropertyTypeNotSameException {

        try {
            OntologyActionBo ontologyActionBo = new OntologyActionBo();
            BeanUtils.copyProperties(param, ontologyActionBo);
            if (param.getMappingIns() != null && param.getMappingIns().size() > 0) {
                List<OntologyActionMappingInBO> collect = param.getMappingIns().stream().map(item -> {
                    OntologyActionMappingInBO ontologyActionMappingInBO = new OntologyActionMappingInBO();
                    BeanUtils.copyProperties(item, ontologyActionMappingInBO);
                    return ontologyActionMappingInBO;
                }).collect(Collectors.toList());
                ontologyActionBo.setMappingIns(collect);
            }
            return DataResult.ofData(ontologyActionService.save(ontologyActionBo));
        } catch (BaseException e) {
            return DataResult.fail(e.getMessage(), e.code, e.getRootCauseMessage());
        }
    }

    @ApiOperation(value = "修改行为")
    @PutMapping("/meta")
    public ApiResult metaUpdate(@RequestBody ActionAddParam param) throws OntologyFunctionParameterPropertyTypeNotSameException {

        OntologyActionBo ontologyActionBo = new OntologyActionBo();
        BeanUtils.copyProperties(param, ontologyActionBo);
        ontologyActionBo.setApi(null);
        if (param.getMappingIns() != null && param.getMappingIns().size() > 0) {
            List<OntologyActionMappingInBO> collect = param.getMappingIns().stream().map(item -> {
                OntologyActionMappingInBO ontologyActionMappingInBO = new OntologyActionMappingInBO();
                BeanUtils.copyProperties(item, ontologyActionMappingInBO);
                return ontologyActionMappingInBO;
            }).collect(Collectors.toList());
            ontologyActionBo.setMappingIns(collect);
        }
        return DataResult.ofData(ontologyActionService.update(ontologyActionBo));
    }

    @ApiOperation(value = "依据id删除行为")
    @DeleteMapping("/meta/{id}")
    public DataResult<Integer> delete(@PathVariable Long id) {
        return DataResult.ofData(ontologyActionService.delete(id));
    }

    //读取函数列表
    @ApiOperation(value = "根据api获取行为")
    @GetMapping("/meta/{apiName}")
    public ApiResult getMetadataByApi(@PathVariable String apiName) {
        try {
            return DataResult.ofData(ontologyActionService.getMetadataByApi(apiName));
        } catch (BaseException e) {
            return DataResult.fail(e.getMessage(), e.code, e.getRootCauseMessage());
        }
    }

    @ApiOperation(value = "行为列表")
    @GetMapping("/meta/list")
    public DataResult<PageInfo<OntologyActionVO>> queryMetadataList(@RequestParam(required = false, defaultValue = "1") Integer page, @RequestParam(required = false, defaultValue = "10") Integer size) {

        return DataResult.ofData(ontologyActionService.metaList(page, size));
    }

    @ApiOperation(value = "根据api获取行为参数")
    @GetMapping("/parameter/{apiName}")
    public ApiResult getParameterByApi(@PathVariable String apiName) {
        try {
            return DataResult.ofData(ontologyActionService.getParametersByApi(apiName));
        } catch (BaseException e) {
            return DataResult.fail(e.getMessage(), e.code, e.getRootCauseMessage());
        }
    }

    @ApiOperation(value = "获取本体关联的行为")
    @GetMapping("/by_ontology")
    public ApiResult queryByOntologyUniqueIdentifier(@RequestParam @ApiParam(value = "本体identifier", required = true) String ontologyUniqueIdentifier) {
        try {
            return DataResult.ofData(ontologyActionService.queryByOntologyUniqueIdentifier(ontologyUniqueIdentifier));
        } catch (BaseException e) {
            return DataResult.fail(e.getMessage(), e.code, e.getRootCauseMessage());
        }
    }

    @ApiOperation(value = "配置行为执行逻辑")
    @PostMapping("/config")
    public ApiResult config(@RequestBody ActionHandleConfigParam param) {

        switch (param.getHandleType()) {
            case RULE:
                return DataResult.ofData(ontologyActionService.configRule(param.getActionApi(), param.getObjectPrimaryKeys(), param.getRules(), param.getRuleConnectType()));
            case TASK:
                return DataResult.ofData(ontologyActionService.configTask(param.getActionApi(), param.getObjectPrimaryKeys(), param.getTaskStartTime(), param.getTaskEndTime(), param.getTaskCorn()));
            default:
                return DataResult.ofData("操作不允许");
        }
    }


    @ApiOperation(value = "更新行为规则")
    @PostMapping("/updateActionRules")
    public ApiResult updateActionRules(@RequestBody ActionHandleRule rule) {
        ontologyActionService.updateActionRulesById(rule);
        return ApiResult.fail("success");
    }


    @ApiOperation(value = "查询行为信息")
    @PostMapping("/getActionRulesByIds")
    public ApiResult getActionByIds(String ids) {
        if (ids.trim().length()==0){
            return ApiResult.fail("参数错误");
        }
        String[] idArr = ids.split(",");
        List<ActionHandleRule> rules = ontologyActionService.getActionRulesById(Arrays.asList(idArr));
        if (rules==null || rules.size()==0){
            return ApiResult.fail("没有匹配到行为信息");
        }
        return DataResult.ofData(rules);
    }

    @ApiOperation(value = "执行行为")
    @PostMapping("/execute")
    public ApiResult execute(@RequestBody ActionHandleParam param) {
        try {
            return DataResult.ofData(ontologyActionService.handle(param.getPrimaryKey(), param.getApi(), param.getParams()));
        } catch (BaseException e) {
            return DataResult.fail(e.getMessage(), e.code, e.getRootCauseMessage());
        }
    }

    @ApiOperation(value = "启动行为（定时任务）")
    @GetMapping("/task/start/{api}")
    public ApiResult execute(@PathVariable String api) throws
            FunctionNotFoundException,
            OntologyFunctionNotFoundException,
            FunctionFileNotCompiled,
            OntologyFunctionMappedPropertyNotFoundException,
            FunctionClassNotNewInstanceException,
            SchedulerException {

        OntologyActionVO actionVO = ontologyActionService.getMetadataByApi(api);
        ActionHandleTaskBO actionHandleTaskBO = actionHandleTaskService.selectByActionId(actionVO.getId());
        boolean res = dynamicActionTaskService.addActionTask(actionHandleTaskBO.getId(), actionHandleTaskBO.getCorn());
        if (res) {
            return DataResult.success();
        } else {
            return DataResult.fail("行为执行失败");
        }
    }

    @ApiOperation(value = "停止行为（定时任务）")
    @GetMapping("/task/stop/{api}")
    public ApiResult stop(@PathVariable String api) throws
            SchedulerException,
            FunctionNotFoundException,
            OntologyFunctionNotFoundException,
            FunctionFileNotCompiled,
            OntologyFunctionMappedPropertyNotFoundException,
            FunctionClassNotNewInstanceException {

        OntologyActionVO actionVO = ontologyActionService.getMetadataByApi(api);
        ActionHandleTaskBO actionHandleTaskBO = actionHandleTaskService.selectByActionId(actionVO.getId());
        boolean res = dynamicActionTaskService.removeActionTask(actionHandleTaskBO.getId());
        if (res) {
            return DataResult.success();
        } else {
            return DataResult.fail("行为停止失败");
        }
    }
}
