package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.controller.validator.OntologyIdVerify;
import com.aircas.ptr.foundry.ontology.model.enums.ActionKindEnum;
import com.aircas.ptr.foundry.ontology.model.enums.ActionSchedulingTypeEnum;
import com.aircas.ptr.foundry.ontology.model.enums.TaskStatusEnum;
import com.aircas.ptr.foundry.ontology.model.param.ActionCreateOrUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionSchedulingCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionSchedulingUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.ActionTestParam;
import com.aircas.ptr.foundry.ontology.model.param.OntologyActionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.TreeBatchExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.TreeExecuteParam;
import com.aircas.ptr.foundry.ontology.model.vo.*;
import com.aircas.ptr.foundry.ontology.service.OntologyActionService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;


@Tag(name = "对象行为")
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

    @Operation(summary = "发布或下线行为树")
    @PostMapping("/publish")
    public RestResult publishTree(@RequestParam(required = true, name = "ontologySpaceId") @Parameter(description = "对象空间id") Integer ontologySpaceId,
                                  @RequestParam(required = true, name = "actionApi") @Parameter(description = "行为树api") String actionApi,
                                  @RequestParam(required = true, name = "status") @Parameter(description = "1 发布，0 下线") Integer status) {
        ontologyActionService.publishTree(ontologySpaceId, actionApi, status);
        return RestResult.success();
    }

    @Operation(summary = "发布行为（草稿 -> 已发布，发布后对智能体和调度可用）")
    @PutMapping("/publish")
    public RestResult publishAction(@RequestParam(required = true, name = "ontologySpaceId") @Parameter(description = "对象空间id") Integer ontologySpaceId,
                                    @RequestParam(required = true, name = "actionApi") @Parameter(description = "行为api") String actionApi) {
        ontologyActionService.publishAction(ontologySpaceId, actionApi);
        return RestResult.success();
    }

    @Operation(summary = "撤回发布（已发布 -> 草稿）")
    @PutMapping("/unpublish")
    public RestResult unpublishAction(@RequestParam(required = true, name = "ontologySpaceId") @Parameter(description = "对象空间id") Integer ontologySpaceId,
                                      @RequestParam(required = true, name = "actionApi") @Parameter(description = "行为api") String actionApi) {
        ontologyActionService.unpublishAction(ontologySpaceId, actionApi);
        return RestResult.success();
    }


    @Operation(summary = "依据actionApi删除行为")
    @DeleteMapping("")
    public RestResult delete(@RequestParam(required = true, name = "ontologySpaceId") @Parameter(description = "对象空间id") Integer ontologySpaceId,
                             @RequestParam(required = true, name = "actionApi") @Parameter(description = "行为api") String actionApi) {
        //已经被行为调度的行为不可直接编辑，需要先暂停调度
        ontologyActionService.deleteActionByApi(ontologySpaceId, actionApi);
        return RestResult.success();
    }

    @Operation(summary = "分页获取行为列表（支持按空间、对象、分类、关键词、类别和状态筛选）")
    @GetMapping("/list")
    public RestResult<Page<OntologyActionInfoVO>> pageGetActionByOntologyId(@RequestParam(required = false, name = "ontologySpaceId") @Parameter(description = "对象空间id") Integer ontologySpaceId,
                                                                            @RequestParam(required = false, name = "ontologyId") @Parameter(description = "对象唯一标识符") String ontologyId,
                                                                            @RequestParam(required = false, name = "categoryId") @Parameter(description = "行为分类id") Integer categoryId,
                                                                            @RequestParam(required = false, name = "keyword") @Parameter(description = "关键词：行为名称、算子或描述") String keyword,
                                                                            @RequestParam(required = false, name = "status") @Parameter(description = "行为状态：2 草稿，1 已发布，不传查全部") Integer status,
                                                                            @RequestParam(required = false, name = "actionKind") @Parameter(description = "行为类别：BASE/APPLICATION/TREE，不传查全部") ActionKindEnum actionKind,
                                                                            @RequestParam(required = false, name = "actionKinds") @Parameter(description = "行为类别多选：BASE/APPLICATION/TREE；与 actionKind 同时传时合并，不传查全部") List<ActionKindEnum> actionKinds,
                                                                            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                                                            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {

        // 单值和多选两个入口都支持，避免调用方按 actionKind 传时被静默忽略
        var kinds = Stream.concat(
                        actionKind == null ? Stream.<ActionKindEnum>empty() : Stream.of(actionKind),
                        actionKinds == null ? Stream.<ActionKindEnum>empty() : actionKinds.stream())
                .distinct().toList();
        return RestResult.ofData(ontologyActionService.pageGetActionByOntologyId(ontologySpaceId, ontologyId, categoryId, keyword, status, kinds, pageNum, pageSize));
    }


    @Operation(summary = "根据actionApi获取行为详情")
    @GetMapping("")
    public RestResult<OntologyActionDetailVO> getActionByApi(@RequestParam(required = true, name = "ontologySpaceId") @Parameter(description = "对象空间id") Integer ontologySpaceId,
                                                             @RequestParam(required = true, name = "actionApi") @Parameter(description = "行为api") String actionApi) {
        return RestResult.ofData(ontologyActionService.getActionByApi(ontologySpaceId, actionApi));
    }

    @Operation(summary = "执行对象单个行为")
    @PostMapping("/execute")
    public RestResult executeAction(@RequestBody @Valid OntologyActionExecuteParam param) {
        ontologyActionService.executeAction(param);
        return RestResult.success();
    }


    @Operation(summary = "行为测试（真实落库）")
    @PostMapping("/test")
    public RestResult<ActionTestResultVO> testAction(@RequestBody @Valid ActionTestParam param) {
        return RestResult.ofData(ontologyActionService.testAction(param));
    }


    @Operation(summary = "执行行为树（单轮，真实落库）")
    @PostMapping("/tree/execute")
    public RestResult<TreeExecuteResultVO> executeTree(@RequestBody @Valid TreeExecuteParam param) {
        return RestResult.ofData(ontologyActionService.executeTree(param));
    }


    @Operation(summary = "行为树测试（草稿也可试跑，真实落库）")
    @PostMapping("/tree/test")
    public RestResult<TreeExecuteResultVO> testTree(@RequestBody @Valid TreeExecuteParam param) {
        return RestResult.ofData(ontologyActionService.testTree(param));
    }


    @Operation(summary = "批量执行行为树（按对象类型遍历实例，真实落库）")
    @PostMapping("/tree/executeBatch")
    public RestResult<TreeBatchExecuteResultVO> executeTreeBatch(@RequestBody @Valid TreeBatchExecuteParam param) {
        return RestResult.ofData(ontologyActionService.executeTreeBatch(param));
    }


    @Operation(summary = "行为树编辑器目录：节点类型与本空间可绑定行为")
    @GetMapping("/tree/node-defs")
    public RestResult<TreeDefsVO> getTreeDefs(@RequestParam(required = true, name = "ontologySpaceId") @Parameter(description = "对象空间id") Integer ontologySpaceId) {
        return RestResult.ofData(ontologyActionService.getTreeDefs(ontologySpaceId));
    }


    @Operation(summary = "创建行为调度")
    @PostMapping("/scheduling")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "行为调度创建请求；task 与 rule 按 type 二选一填充",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ActionSchedulingCreateParam.class),
                    examples = {
                            @ExampleObject(name = "rule", summary = "规则调度示例", value = """
                                    {"ontologyIdentifier":"abcdef","actionApi":"shipLocation","name":"示例调度","description":"按规则触发","type":"RULE","rule":{"ruleConnectType":"AND","rules":[{"columnName":"longitude","columnValue":"20.0","condition":"CH"}]}}"""),
                            @ExampleObject(name = "task", summary = "周期调度示例", value = """
                                    {"ontologyIdentifier":"abcdef","actionApi":"shipLocation","name":"示例周期调度","description":"按 cron 周期触发","type":"TASK","task":{"taskCronExpression":"30 * * 1/1 * ? *"}}""")
                    }))
    public RestResult<Long> createScheduling(@RequestBody @Valid ActionSchedulingCreateParam param) {
        var id = ontologyActionService.createScheduling(param);
        return RestResult.ofData(id);
    }

    @Operation(summary = "编辑行为调度")
    @PutMapping("/scheduling")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "行为调度编辑请求；task 与 rule 按 type 二选一填充",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ActionSchedulingUpdateParam.class),
                    examples = {
                            @ExampleObject(name = "rule", summary = "规则调度示例", value = """
                                    {"id":123,"ontologyIdentifier":"abcdef","actionApi":"shipLocation","name":"示例调度","description":"按规则触发","type":"RULE","rule":{"ruleConnectType":"AND","rules":[{"columnName":"longitude","columnValue":"20.0","condition":"CH"}]}}"""),
                            @ExampleObject(name = "task", summary = "周期调度示例", value = """
                                    {"id":123,"ontologyIdentifier":"abcdef","actionApi":"shipLocation","name":"示例周期调度","description":"按 cron 周期触发","type":"TASK","task":{"taskCronExpression":"30 * * 1/1 * ? *"}}""")
                    }))
    public RestResult updateScheduling(@RequestBody @Valid ActionSchedulingUpdateParam param) {
        ontologyActionService.updateScheduling(param);
        return RestResult.success();
    }


    @Operation(summary = "分页查看行为调度列表")
    @GetMapping("/scheduling/list")
    public RestResult<Page<ActionSchedulingInfoVO>> listScheduling(@RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                                                   @RequestParam(required = false, defaultValue = "10") Integer pageSize,
                                                                   @RequestParam(required = false) @Parameter(description = "调度名称，模糊匹配") String name,
                                                                   @RequestParam(required = false) @Parameter(description = "TASK/RULE，不传查全部") ActionSchedulingTypeEnum type) {

        Page<ActionSchedulingInfoVO> result = ontologyActionService.listScheduling(pageNum, pageSize, name, type);
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
                                                                    @RequestParam(required = false, name = "taskStatus") @Parameter(description = "执行状态过滤，可选") TaskStatusEnum taskStatus,
                                                                    @RequestParam(required = false, name = "beginTime") @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") @Parameter(description = "触发时间起，yyyy-MM-dd HH:mm:ss，可选") Date beginTime,
                                                                    @RequestParam(required = false, name = "endTime") @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") @Parameter(description = "触发时间止，yyyy-MM-dd HH:mm:ss，可选") Date endTime,
                                                                    @RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                                                    @RequestParam(required = false, defaultValue = "10") Integer pageSize) {

        Page<SchedulingResultVO> result = ontologyActionService.getSchedulingResult(id, type, taskStatus, beginTime, endTime, pageNum, pageSize);
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
