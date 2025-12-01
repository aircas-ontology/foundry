package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.param.ActionCreateOrUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionSchedulingCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionSchedulingUpdateParam;
import com.aircas.ptr.foundry.ontology.model.vo.ActionSchedulingDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.ActionSchedulingInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyActionDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.OntologyActionInfoVO;
import com.aircas.ptr.foundry.ontology.service.ActionHandleTaskService;
import com.aircas.ptr.foundry.ontology.service.DynamicActionTaskService;
import com.aircas.ptr.foundry.ontology.service.OntologyActionService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;


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


    @ApiOperation(value = "执行行为")
    @PostMapping(value = "/execute/{actionApi}")
    public RestResult executeAction(@PathVariable(name = "actionApi", required = true) String actionApi) {
        return RestResult.success();
    }


    @ApiOperation(value = "新增行为")
    @PostMapping("")
    public RestResult createAction(@RequestBody @Valid ActionCreateOrUpdateParam param) {
        ontologyActionService.createAction(param);
        return RestResult.success();
    }


    @ApiOperation(value = "编辑行为")
    @PutMapping("")
    public RestResult updateAction(@RequestBody @Valid ActionCreateOrUpdateParam param) {
        //已经被行为调度的行为不可直接编辑，需要先暂停调度
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
    @DeleteMapping("/{actionApi}")
    public RestResult delete(@PathVariable(required = true, name = "actionApi") String actionApi) {
        //已经被行为调度的行为不可直接编辑，需要先暂停调度
        return RestResult.success();
    }

    @ApiOperation(value = "分页获取本体下行为列表")
    @GetMapping("/list")
    public RestResult<Page<OntologyActionInfoVO>> pageGetActionByOntologyId(@RequestParam(required = true, name = "ontologyUniqIdentifier") @ApiParam(name = "ontologyUniqIdentifier", value = "本体id", required = true) String ontologyUniqIdentifier,
                                                                            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                                                            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
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
    public RestResult<OntologyActionDetailVO> getActionById(@RequestParam(required = true, name = "actionId") @ApiParam(name = "actionId", value = "行为id", required = true) String actionId) {
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


    @ApiOperation(value = "创建行为调度")
    @PostMapping("/scheduling")
    public RestResult<Long> createScheduling(@RequestBody @Valid ActionSchedulingCreateParam param) {
        return RestResult.success();
    }

    @ApiOperation(value = "编辑行为调度")
    @PutMapping("/scheduling")
    public RestResult updateScheduling(@RequestBody @Valid ActionSchedulingUpdateParam param) {
        return RestResult.success();
    }


    @ApiOperation(value = "分页查看行为调度列表")
    @GetMapping("/scheduling/list")
    public RestResult<Page<ActionSchedulingInfoVO>> pageGetScheduling(@RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                                                      @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return RestResult.success();
    }


    @ApiOperation(value = "查询行为调度详情")
    @GetMapping("/scheduling/detail")
    public RestResult<ActionSchedulingDetailVO> getSchedulingDetailById(@RequestParam(required = true, name = "id") @ApiParam(name = "id", value = "id", required = true) Long id) {
        return RestResult.success();
    }


    @ApiOperation(value = "删除行为调度")
    @DeleteMapping("/scheduling/{id}")
    public RestResult deleteSchedulingById(@PathVariable(name = "id") Long id) {
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

    @ApiOperation(value = "查询行为执行结果数据，默认10条最新结果")
    @GetMapping("/scheduling/result")
    public RestResult<Page<String>> getSchedulingResult(@RequestParam(required = true, name = "id") @ApiParam(name = "id", value = "scheduling id", required = true) String id,
                                                        @RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                                        @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return RestResult.success();
    }


    @ApiOperation(value = "启动行为调度")
    @PostMapping("/scheduling/start/{id}")
    public RestResult startScheduling(@PathVariable(name = "id") Long id) {
        return RestResult.success();
    }


    @ApiOperation(value = "暂停行为调度")
    @PostMapping("/scheduling/stop/{id}")
    public RestResult stopScheduling(@PathVariable(name = "id") Long id) {
        return RestResult.success();
    }


//    @ApiOperation(value = "更新行为执行数据")
//    @PostMapping("/updateActionDataLogByAction")
//    public ApiResult getActionDataLogByAction(@RequestBody ActionHandleCommitFlashMemory memory) {
//        return DataResult.ofData(ontologyActionService.updateActionDataById(memory));
//    }


//    @ApiOperation(value = "手动执行行为函数")
//    @PostMapping(value = "/execute")
//    public ApiResult execute(@RequestBody @Valid ActionHandleExecuteParam param) {
//        return ApiResult.success();
//    }

//    @ApiOperation(value = "启动行为（定时任务）")
//    @PostMapping("/task/start/{api}")
//    public RestResult execute(@PathVariable String api) throws
//            FunctionNotFoundException,
//            OntologyFunctionNotFoundException,
//            FunctionFileNotCompiled,
//            OntologyFunctionMappedPropertyNotFoundException,
//            FunctionClassNotNewInstanceException,
//            SchedulerException {
//
//        OntologyActionVO actionVO = ontologyActionService.getMetadataByApi(api);
//        ActionHandleTaskBO actionHandleTaskBO = actionHandleTaskService.selectByActionId(actionVO.getId());
//        boolean res = dynamicActionTaskService.addActionTask(actionHandleTaskBO.getId(), actionHandleTaskBO.getCorn());
//        if (res) {
//            return RestResult.success();
//        } else {
//            return RestResult.failed();
//        }
//    }
//
//    @ApiOperation(value = "停止行为（定时任务）")
//    @PostMapping("/task/stop/{api}")
//    public RestResult stop(@PathVariable String api) throws
//            SchedulerException,
//            FunctionNotFoundException,
//            OntologyFunctionNotFoundException,
//            FunctionFileNotCompiled,
//            OntologyFunctionMappedPropertyNotFoundException,
//            FunctionClassNotNewInstanceException {
//
//        OntologyActionVO actionVO = ontologyActionService.getMetadataByApi(api);
//        ActionHandleTaskBO actionHandleTaskBO = actionHandleTaskService.selectByActionId(actionVO.getId());
//        boolean res = dynamicActionTaskService.removeActionTask(actionHandleTaskBO.getId());
//        if (res) {
//            return RestResult.success();
//        } else {
//            return RestResult.failed();
//        }
//    }

//    @ApiOperation(value = "当前定时执行的行为列表")
//    @GetMapping("/task/list")
//    public RestResult<Map<Long, JobKey>> list() {
//
//        return RestResult.ofData(dynamicActionTaskService.listActionTask());
//    }
}
