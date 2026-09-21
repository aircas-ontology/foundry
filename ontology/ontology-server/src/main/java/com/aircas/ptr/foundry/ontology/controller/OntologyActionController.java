package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import com.aircas.ptr.foundry.ontology.model.enums.ActionSchedulingTypeEnum;
import com.aircas.ptr.foundry.ontology.model.param.ActionCreateOrUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionSchedulingCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionSchedulingUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyActionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.aircas.ptr.foundry.ontology.service.OntologyActionService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;


@Tag(name = "本体行为")
@RestController
@RequestMapping("/action")
public class OntologyActionController {

    @Resource
    private OntologyActionService ontologyActionService;


    @Operation(summary = "新增行为")
    @PostMapping("")
    public RestResult createAction(@RequestBody @Valid ActionCreateOrUpdateParam param) {
        ontologyActionService.createAction(param);
        return RestResult.success();
    }


    @Operation(summary = "编辑行为")
    @PutMapping("")
    public RestResult updateAction(@RequestBody @Valid ActionCreateOrUpdateParam param) {
        //已经被行为调度的行为不可直接编辑，需要先暂停调度
        ontologyActionService.updateAction(param);
        return RestResult.success();
    }

    @Operation(summary = "依据actionApi删除行为")
    @DeleteMapping("/{actionApi}")
    public RestResult delete(@PathVariable(required = true, name = "actionApi") String actionApi) {
        //已经被行为调度的行为不可直接编辑，需要先暂停调度
        ontologyActionService.deleteActionByApi(actionApi);
        return RestResult.success();
    }

    @Operation(summary = "分页获取本体下行为列表")
    @GetMapping("/list")
    public RestResult<Page<OntologyActionInfoVO>> pageGetActionByOntologyId(@RequestParam(required = true, name = "ontologyUniqIdentifier") @Parameter(description = "本体id") @OntologyIdVerify String ontologyUniqIdentifier,
                                                                            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                                                            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {

        return RestResult.ofData(ontologyActionService.pageGetActionByOntologyId(ontologyUniqIdentifier, pageNum, pageSize));
    }


    @Operation(summary = "根据actionApi获取行为详情")
    @GetMapping("")
    public RestResult<OntologyActionDetailVO> getActionByApi(@RequestParam(required = true, name = "actionApi") @Parameter(description = "行为api") String actionApi) {
        return RestResult.ofData(ontologyActionService.getActionByApi(actionApi));
    }

    @Operation(summary = "执行本体单个行为")
    @PostMapping("/execute")
    public RestResult executeAction(@RequestBody @Valid OntologyActionExecuteParam param) {
        ontologyActionService.executeAction(param);
        return RestResult.success();
    }


    @Operation(summary = "创建行为调度")
    @PostMapping("/scheduling")
    public RestResult<Long> createScheduling(@RequestBody @Valid ActionSchedulingCreateParam param) {
        var id = ontologyActionService.createScheduling(param);
        return RestResult.ofData(id);
    }

    @Operation(summary = "编辑行为调度")
    @PutMapping("/scheduling")
    public RestResult updateScheduling(@RequestBody @Valid ActionSchedulingUpdateParam param) {
        ontologyActionService.updateScheduling(param);
        return RestResult.success();
    }


    @Operation(summary = "分页查看行为调度列表")
    @GetMapping("/scheduling/list")
    public RestResult<Page<ActionSchedulingInfoVO>> listScheduling(@RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                                                   @RequestParam(required = false, defaultValue = "10") Integer pageSize) {

        Page<ActionSchedulingInfoVO> result = ontologyActionService.listScheduling(pageNum, pageSize);
        return RestResult.ofData(result);
    }


    @Operation(summary = "查询行为调度详情")
    @GetMapping("/scheduling/detail")
    public RestResult<ActionSchedulingDetailVO> getSchedulingDetailById(@RequestParam(required = true, name = "id") @Parameter(description = "id") Long id,
                                                                        @RequestParam(required = true, name = "type") @Parameter(description = "TASK/RULE") ActionSchedulingTypeEnum type) {
        ActionSchedulingDetailVO vo = ontologyActionService.getSchedulingDetailById(id, type);
        return RestResult.ofData(vo);
    }


    @Operation(summary = "删除行为调度")
    @DeleteMapping("/scheduling/{type}/{id}")
    public RestResult deleteScheduling(@PathVariable(name = "type") ActionSchedulingTypeEnum type, @PathVariable(name = "id") Long id) {
        ontologyActionService.removeScheduling(type, id);
        return RestResult.success();
    }


    @Operation(summary = "查询行为执行结果数据，默认10条最新结果")
    @GetMapping("/scheduling/result")
    public RestResult<Page<SchedulingResultVO>> getSchedulingResult(@RequestParam(required = true, name = "id") @Parameter(description = "scheduling id") Long id,
                                                                    @RequestParam(required = true, name = "type") @Parameter(description = "TASK/RULE") ActionSchedulingTypeEnum type,
                                                                    @RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                                                    @RequestParam(required = false, defaultValue = "10") Integer pageSize) {

        Page<SchedulingResultVO> result = ontologyActionService.getSchedulingResult(id, type, pageNum, pageSize);
        return RestResult.ofData(result);
    }


    @Operation(summary = "启动行为调度")
    @PostMapping("/scheduling/start/{type}/{id}")
    public RestResult startScheduling(@PathVariable(name = "type") ActionSchedulingTypeEnum type, @PathVariable(name = "id") Long id) {
        ontologyActionService.startScheduling(type, id);
        return RestResult.success();
    }


    @Operation(summary = "暂停行为调度")
    @PostMapping("/scheduling/stop/{type}/{id}")
    public RestResult stopScheduling(@PathVariable(name = "type") ActionSchedulingTypeEnum type, @PathVariable(name = "id") Long id) {
        ontologyActionService.stopScheduling(type, id);
        return RestResult.success();
    }

}
