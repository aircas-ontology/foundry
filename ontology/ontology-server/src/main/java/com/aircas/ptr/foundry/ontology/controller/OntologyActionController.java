package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.ApiResult;
import com.aircas.ptr.foundry.common.base.DataResult;
import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.model.po.ActionHandleCommitFlashMemory;
import com.aircas.ptr.foundry.model.po.ActionHandleRule;
import com.aircas.ptr.foundry.model.po.OntologyAction;
import com.aircas.ptr.foundry.ontology.exception.*;
import com.aircas.ptr.foundry.ontology.model.bo.ActionHandleTaskBO;
import com.aircas.ptr.foundry.ontology.model.bo.OntologyActionBo;
import com.aircas.ptr.foundry.ontology.model.bo.OntologyActionMappingInBO;
import com.aircas.ptr.foundry.ontology.model.param.*;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.aircas.ptr.foundry.ontology.service.ActionHandleTaskService;
import com.aircas.ptr.foundry.ontology.service.DynamicActionTaskService;
import com.aircas.ptr.foundry.ontology.service.OntologyActionService;
import com.github.pagehelper.PageInfo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.quartz.JobKey;
import org.quartz.SchedulerException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    @PostMapping("")
    public RestResult createAction(@RequestBody @Valid ActionCreateParam param) {
        return RestResult.success();
    }


    @ApiOperation(value = "编辑行为")
    @PutMapping("")
    public RestResult updateAction(@RequestBody @Valid ActionUpdateParam param) {
        return RestResult.success();
    }


//    @ApiOperation(value = "新增行为")
//    @PostMapping("/meta")
//    public ApiResult metaSave(@RequestBody ActionAddParam param) throws OntologyFunctionParameterPropertyTypeNotSameException {
//
//        try {
//            OntologyActionBo ontologyActionBo = new OntologyActionBo();
//            BeanUtils.copyProperties(param, ontologyActionBo);
//            if (param.getMappingIns() != null && param.getMappingIns().size() > 0) {
//                List<OntologyActionMappingInBO> collect = param.getMappingIns().stream().map(item -> {
//                    OntologyActionMappingInBO ontologyActionMappingInBO = new OntologyActionMappingInBO();
//                    BeanUtils.copyProperties(item, ontologyActionMappingInBO);
//                    return ontologyActionMappingInBO;
//                }).collect(Collectors.toList());
//                ontologyActionBo.setMappingIns(collect);
//            }
//            return DataResult.ofData(ontologyActionService.save(ontologyActionBo));
//        } catch (BaseException e) {
//            return DataResult.fail(e.getMessage(), e.code, e.getRootCauseMessage());
//        }
//    }

//    @ApiOperation(value = "修改行为")
//    @PutMapping("/meta")
//    public ApiResult metaUpdate(@RequestBody ActionAddParam param) throws OntologyFunctionParameterPropertyTypeNotSameException {
//
//        OntologyActionBo ontologyActionBo = new OntologyActionBo();
//        BeanUtils.copyProperties(param, ontologyActionBo);
//        ontologyActionBo.setApi(null);
//        if (param.getMappingIns() != null && param.getMappingIns().size() > 0) {
//            List<OntologyActionMappingInBO> collect = param.getMappingIns().stream().map(item -> {
//                OntologyActionMappingInBO ontologyActionMappingInBO = new OntologyActionMappingInBO();
//                BeanUtils.copyProperties(item, ontologyActionMappingInBO);
//                return ontologyActionMappingInBO;
//            }).collect(Collectors.toList());
//            ontologyActionBo.setMappingIns(collect);
//        }
//        return DataResult.ofData(ontologyActionService.update(ontologyActionBo));
//    }

    @ApiOperation(value = "依据id删除行为")
    @DeleteMapping("/{actionId}")
    public RestResult delete(@PathVariable(required = true,name = "actionId") Long actionId) {
        return RestResult.success();
    }

    @ApiOperation(value = "根据本体id获取所有行为")
    @GetMapping("/list")
    public RestResult<List<OntologyActionInfoVO>> getActionByOntologyId(@RequestParam(required = false,name = "ontologyId") @ApiParam(name = "ontologyId",value = "本体id",required = false) String ontologyId) {
        return RestResult.success();
    }

//    //读取函数列表
//    @ApiOperation(value = "根据api name获取行为")
//    @GetMapping("/meta/{apiName}")
//    public ApiResult getMetadataByApi(@PathVariable String apiName) {
//        try {
//            return DataResult.ofData(ontologyActionService.getMetadataByApi(apiName));
//        } catch (BaseException e) {
//            return DataResult.fail(e.getMessage(), e.code, e.getRootCauseMessage());
//        }
//    }

//    @ApiOperation(value = "行为列表")
//    @GetMapping("/meta/list")
//    public DataResult<PageInfo<OntologyActionVO>> queryMetadataList(@RequestParam(required = false, defaultValue = "1") Integer page, @RequestParam(required = false, defaultValue = "10") Integer size) {
//
//        return DataResult.ofData(ontologyActionService.metaList(page, size));
//    }

    @ApiOperation(value = "根据actionId获取行为")
    @GetMapping("")
    public RestResult<OntologyActionInfoVO> getActionById(@RequestParam(required = true,name = "actionId") @ApiParam(name = "actionId",value = "行为id",required = true) String actionId) {
        return RestResult.success();
    }

//    @ApiOperation(value = "获取本体关联的行为")
//    @GetMapping("/by_ontology")
//    public ApiResult queryByOntologyUniqueIdentifier(@RequestParam @ApiParam(value = "本体identifier", required = true) String ontologyUniqueIdentifier) {
//        try {
//            return DataResult.ofData(ontologyActionService.queryByOntologyUniqueIdentifier(ontologyUniqueIdentifier));
//        } catch (BaseException e) {
//            return DataResult.fail(e.getMessage(), e.code, e.getRootCauseMessage());
//        }
//    }

    @ApiOperation(value = "配置行为task执行逻辑")
    @PostMapping("/config/task")
    public RestResult configTask(@RequestBody @Valid ActionHandleTaskParam param) {
        return RestResult.success();
    }

    @ApiOperation(value = "查询行为task执行逻辑")
    @GetMapping("/config/task")
    public RestResult<ActionHandleTaskVO> queryTask(@RequestParam(required = true,name = "actionId") @ApiParam(name = "actionId",value = "行为id",required = true) String actionId) {
        return RestResult.success();
    }


    @ApiOperation(value = "配置行为task执行逻辑")
    @PostMapping("/config/rule")
    public RestResult configRule(@RequestBody @Valid ActionHandleRuleParam param) {
        return RestResult.success();
    }

    @ApiOperation(value = "查询行为rule执行逻辑")
    @GetMapping("/config/rule")
    public RestResult<ActionHandleRuleVO> queryRule(@RequestParam(required = true,name = "actionId") @ApiParam(name = "actionId",value = "行为id",required = true) String actionId) {
        return RestResult.success();
    }



//    @ApiOperation(value = "更新行为规则")
//    @PostMapping("/updateActionRules")
//    public ApiResult updateActionRules(@RequestBody ActionHandleRule rule) {
//        ontologyActionService.updateActionRulesById(rule);
//        return ApiResult.fail("success");
//    }


//    @ApiOperation(value = "查询行为规则信息")
//    @GetMapping("/getActionRulesByIds")
//    public ApiResult getActionByIds(String ids) {
//        if (ids.trim().length() == 0) {
//            return ApiResult.fail("参数错误");
//        }
//        String[] idArr = ids.split(",");
//        List<Long> idList = new ArrayList<>(idArr.length);
//        for (String id : idArr) {
//            idList.add(Long.parseLong(id));
//        }
//        List<ActionHandleRule> rules = ontologyActionService.getActionRulesById(idList);
//        if (rules == null || rules.size() == 0) {
//            return ApiResult.fail("没有匹配到行为信息");
//        }
//        return DataResult.ofData(rules);
//    }

    @ApiOperation(value = "查询行为执行结果数据")
    @GetMapping("/result")
    public RestResult<List<ActionResultVO>> getActionExecuteResult(@RequestParam(required = true,name = "actionId") @ApiParam(name = "actionId",value = "行为id",required = true) String actionId) {
        return RestResult.success();
    }

//    @ApiOperation(value = "更新行为执行数据")
//    @PostMapping("/updateActionDataLogByAction")
//    public ApiResult getActionDataLogByAction(@RequestBody ActionHandleCommitFlashMemory memory) {
//        return DataResult.ofData(ontologyActionService.updateActionDataById(memory));
//    }

//    @ApiOperation(value = "执行行为")
//    @PostMapping(value = "/execute")
//    public ApiResult execute(@RequestBody ActionHandleParam param) {
//        try {
//            return DataResult.ofData(ontologyActionService.handle(param.getPrimaryKey(), param.getApi(), param.getParams()));
//        } catch (BaseException e) {
//            return DataResult.fail(e.getMessage(), e.code, e.getRootCauseMessage());
//        }
//    }

    @ApiOperation(value = "手动补偿执行行为函数")
    @PostMapping(value = "/execute")
    public ApiResult execute(@RequestBody @Valid  ActionHandleExecuteParam param) {
        return ApiResult.success();
    }

    @ApiOperation(value = "启动行为（定时任务）")
    @PostMapping("/task/start/{api}")
    public RestResult execute(@PathVariable String api) throws
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
            return RestResult.success();
        } else {
            return RestResult.failed();
        }
    }

    @ApiOperation(value = "停止行为（定时任务）")
    @PostMapping("/task/stop/{api}")
    public RestResult stop(@PathVariable String api) throws
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
            return RestResult.success();
        } else {
            return RestResult.failed();
        }
    }

    @ApiOperation(value = "当前定时执行的行为列表")
    @GetMapping("/task/list")
    public RestResult<Map<Long, JobKey>> list() {

        return RestResult.ofData(dynamicActionTaskService.listActionTask());
    }
}
