package com.aircas.ptr.foundry.ontology.controller;

import com.aircas.ptr.foundry.common.base.RestResult;
import com.aircas.ptr.foundry.ontology.model.param.FunctionCallbackParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionCreateParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionExecuteParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionUpdateParam;
import com.aircas.ptr.foundry.ontology.model.param.FunctionVersionParam;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionDetailVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionExecuteResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionInfoVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionResultVO;
import com.aircas.ptr.foundry.ontology.model.vo.FunctionVersionVO;
import com.aircas.ptr.foundry.ontology.service.FunctionService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;


@Tag(name = "函数算子管理")
@RestController
@RequestMapping("/function")
public class OntologyFunctionController {

    @Resource
    private FunctionService functionService;


    @PostMapping("/execute")
    @Operation(summary = "函数执行")
    public RestResult<String> executeFunction(@RequestBody @Valid FunctionExecuteParam param) {
        return RestResult.ofData(functionService.executeFunction(param));
    }


    @Operation(summary = "查询函数列表")
    @GetMapping("/list")
    public RestResult<Page<FunctionInfoVO>> getFunctions(@RequestParam(required = false, defaultValue = "1") Integer pageNum,
                                                         @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return RestResult.ofData(functionService.getFunctions(pageNum, pageSize));
    }

    @Operation(summary = "创建函数")
    @PostMapping
    public RestResult createFunction(@RequestBody @Valid FunctionCreateParam param) {
        //todo 需要增加代码安全检测
        functionService.createFunction(param);
        return RestResult.success();
    }

    @Operation(summary = "更新函数")
    @PutMapping
    public RestResult updateFunction(@RequestBody FunctionUpdateParam param) {
        // 需要 1 校验函数有没有被本体行为使用到，否则不能修改 2 需要增加代码安全检测
        functionService.updateFunction(param);
        return RestResult.success();
    }


    @Operation(summary = "根据函数api获取函数详情")
    @GetMapping("/detail")
    public RestResult<FunctionDetailVO> getFunctionByApi(@RequestParam(name = "functionApi") @Parameter(description = "函数api") String functionApi,
                                                         @RequestParam(required = false, name = "versionNo") Integer versionNo) {
        return RestResult.ofData(functionService.getFunctionDetailByApi(functionApi, versionNo));
    }

    @PostMapping("/version_create")
    @Operation(summary = "创建函数草稿版本")
    public RestResult createVersion(@RequestBody @Valid FunctionVersionParam param) { functionService.createDraft(param); return RestResult.success(); }

    @PostMapping("/version_publish")
    @Operation(summary = "发布函数版本")
    public RestResult publishVersion(@RequestBody @Valid FunctionVersionParam param) { functionService.publishVersion(param); return RestResult.success(); }

    @PostMapping("/version_rollback")
    @Operation(summary = "前滚式回滚函数版本")
    public RestResult rollbackVersion(@RequestBody @Valid FunctionVersionParam param) { functionService.rollbackVersion(param); return RestResult.success(); }

    @GetMapping("/version_list")
    @Operation(summary = "查询函数版本列表")
    public RestResult<Page<FunctionVersionVO>> listVersions(@RequestParam String functionApi,
            @RequestParam(defaultValue = "1") Integer pageNum, @RequestParam(defaultValue = "10") Integer pageSize) {
        return RestResult.ofData(functionService.listVersions(functionApi, pageNum, pageSize));
    }


    @Operation(summary = "根据functionApi删除函数")
    @DeleteMapping("/delete/{functionApi}")
    public RestResult deleteById(@PathVariable(required = true, name = "functionApi") String functionApi) {
        //需要校验函数有没有被本体行为使用到
        functionService.deleteByApi(functionApi);
        return RestResult.success();
    }


    @PostMapping("/callback")
    @Operation(summary = "异步函数执行结果回调")
    public RestResult functionCallback(@RequestBody @Valid FunctionCallbackParam param) {
        functionService.callback(param.getResult());
        return RestResult.success();
    }


    @GetMapping("/callback_result")
    @Operation(summary = "根据taskId查询异步函数执行结果")
    public RestResult<FunctionExecuteResultVO> getExecuteResult(@RequestParam(required = true, name = "taskId") @Parameter(description = "任务id") String taskId) {
        return RestResult.ofData(functionService.getExecuteResult(taskId));
    }

}
